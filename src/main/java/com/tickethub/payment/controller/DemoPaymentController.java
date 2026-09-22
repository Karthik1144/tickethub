package com.tickethub.payment.controller;

import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.security.CurrentUser;
import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.service.BookingService;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.dto.PaymentDtos.WebhookAck;
import com.tickethub.payment.dto.PaymentDtos.WebhookPayload;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DEMO ONLY. The mock gateway never calls back on its own, so without this a deployed demo
 * could hold seats but never confirm a booking. This drives the same PaymentService.handleWebhook
 * path a real gateway would, so state transitions, idempotency and late-payment handling are
 * exactly the production code. The bean does not exist unless the "demo" profile is active.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/dev")
@Tag(name = "Demo only")
public class DemoPaymentController {

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public DemoPaymentController(BookingService bookingService,
                                 PaymentRepository paymentRepository,
                                 PaymentService paymentService,
                                 CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @PostMapping("/bookings/{bookingRef}/simulate-payment")
    @Operation(summary = "Simulate the gateway confirming payment (demo profile only)")
    public WebhookAck simulatePayment(@PathVariable String bookingRef) {
        AuthenticatedUser user = currentUser.require();
        Booking booking = bookingService.requireOwnedBooking(bookingRef, user.id(), user.role());

        Payment payment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), PaymentStatus.CREATED)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_BOOKING_STATE,
                        "Start payment for this booking first"));

        return paymentService.handleWebhook(new WebhookPayload(
                UUID.randomUUID().toString(),
                "payment.success",
                payment.getGatewayOrderId(),
                "pay_demo_" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
