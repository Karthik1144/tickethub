package com.tickethub.payment.service;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingTransactionService;
import com.tickethub.booking.service.ConfirmOutcome;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.config.TicketHubProperties;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.domain.WebhookEvent;
import com.tickethub.payment.dto.PaymentDtos.*;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.repository.WebhookEventRepository;
import com.tickethub.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String EVENT_SUCCESS = "payment.success";
    private static final String EVENT_FAILED = "payment.failed";

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final BookingRepository bookingRepository;
    private final BookingTransactionService bookingTransactions;
    private final PaymentGateway gateway;
    private final TicketHubProperties props;

    public PaymentService(PaymentRepository paymentRepository,
                          WebhookEventRepository webhookEventRepository,
                          BookingRepository bookingRepository,
                          BookingTransactionService bookingTransactions,
                          PaymentGateway gateway,
                          TicketHubProperties props) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingTransactions = bookingTransactions;
        this.gateway = gateway;
        this.props = props;
    }

    /**
     * Creates a gateway order for a pending booking.
     * The gateway call happens outside any database transaction, so no locks are held over the network.
     */
    public StartPaymentResponse startPayment(String bookingRef, Long userId, Role role) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> ApiException.notFound("Booking"));

        if (role != Role.ADMIN && !booking.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This booking belongs to another account");
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.ALREADY_CONFIRMED, "This booking is already paid for");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.INVALID_BOOKING_STATE, "This booking can no longer be paid for");
        }
        if (booking.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.HOLD_EXPIRED, "The seat hold has expired, please select seats again");
        }

        // Reuse an existing open order rather than creating a second one.
        Payment existing = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), PaymentStatus.CREATED)
                .orElse(null);
        if (existing != null) {
            return new StartPaymentResponse(existing.getId(), existing.getGateway(), existing.getGatewayOrderId(),
                    existing.getAmount(), existing.getCurrency(),
                    java.util.Map.of("gateway", existing.getGateway(), "orderId", existing.getGatewayOrderId()));
        }

        String idempotencyKey = "bk-" + booking.getId() + "-" + UUID.randomUUID();
        String currency = props.getPayment().getCurrency();
        PaymentGateway.GatewayOrder order = gateway.createOrder(
                booking.getBookingRef(), booking.getTotalAmount(), currency, idempotencyKey);

        Payment payment = savePayment(booking, order.orderId(), idempotencyKey, currency);
        return new StartPaymentResponse(payment.getId(), gateway.name(), order.orderId(),
                booking.getTotalAmount(), currency, order.checkoutPayload());
    }

    /* Spring Data repository calls are each transactional on their own; the multi-step
       state changes that must be atomic live in BookingTransactionService instead. */
    private Payment savePayment(Booking booking, String orderId, String idempotencyKey, String currency) {
        return paymentRepository.save(new Payment(booking, gateway.name(), orderId,
                idempotencyKey, booking.getTotalAmount(), currency));
    }

    /**
     * Applies a verified webhook. Deduplicated on event id, so repeated deliveries
     * have no extra effect (FR-BOOK-05).
     */
    public WebhookAck handleWebhook(WebhookPayload payload) {
        if (!recordEvent(payload.eventId())) {
            log.info("Duplicate webhook {} ignored", payload.eventId());
            return new WebhookAck("DUPLICATE_IGNORED");
        }

        Payment payment = paymentRepository.findByGatewayOrderId(payload.gatewayOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment"));

        return switch (payload.type()) {
            case EVENT_SUCCESS -> applySuccess(payment, payload.gatewayPaymentId());
            case EVENT_FAILED -> {
                markStatus(payment.getId(), PaymentStatus.FAILED, payload.gatewayPaymentId());
                yield new WebhookAck("PAYMENT_FAILED");
            }
            default -> new WebhookAck("IGNORED");
        };
    }

    private WebhookAck applySuccess(Payment payment, String gatewayPaymentId) {
        markStatus(payment.getId(), PaymentStatus.SUCCESS, gatewayPaymentId);
        Long bookingId = payment.getBooking().getId();

        ConfirmOutcome outcome = bookingTransactions.confirmBooking(bookingId);
        if (outcome == ConfirmOutcome.EXPIRED_LATE) {
            // FR-BOOK-06: money taken but the seats are gone, so refund automatically.
            refundPayment(payment.getId());
            return new WebhookAck("REFUNDED_LATE_PAYMENT");
        }
        return new WebhookAck(outcome == ConfirmOutcome.CONFIRMED ? "CONFIRMED" : "ALREADY_CONFIRMED");
    }

    /** Refunds the successful payment of a booking, if there is one. */
    public String refundBooking(Long bookingId) {
        Payment payment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCESS)
                .orElse(null);
        if (payment == null) {
            return "NOT_APPLICABLE";
        }
        refundPayment(payment.getId());
        return "INITIATED";
    }

    private void refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        gateway.refund(payment.getGatewayPaymentId(), payment.getAmount());
        markStatus(paymentId, PaymentStatus.REFUNDED, payment.getGatewayPaymentId());
    }

    private void markStatus(Long paymentId, PaymentStatus status, String gatewayPaymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        payment.setStatus(status);
        if (gatewayPaymentId != null) {
            payment.setGatewayPaymentId(gatewayPaymentId);
        }
        paymentRepository.save(payment);
    }

    /** Returns false when this event was already processed. */
    private boolean recordEvent(String eventId) {
        if (webhookEventRepository.existsByEventId(eventId)) {
            return false;
        }
        try {
            webhookEventRepository.saveAndFlush(new WebhookEvent(eventId));
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Two deliveries raced: the unique key decided the winner.
            return false;
        }
    }
}
