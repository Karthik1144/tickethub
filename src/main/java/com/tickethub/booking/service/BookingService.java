package com.tickethub.booking.service;
import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingItem;
import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.dto.BookingDtos.*;
import com.tickethub.booking.repository.BookingItemRepository;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.common.dto.PageResponse;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.config.TicketHubProperties;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_HOLD_ATTEMPTS = 3;

    private final BookingTransactionService transactions;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final PaymentService paymentService;
    private final QrCodeGenerator qrCodeGenerator;
    private final TicketHubProperties props;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingTransactionService transactions,
                          BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository,
                          PaymentService paymentService,
                          QrCodeGenerator qrCodeGenerator,
                          TicketHubProperties props) {
        this.transactions = transactions;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.paymentService = paymentService;
        this.qrCodeGenerator = qrCodeGenerator;
        this.props = props;
    }

    /**
     * Holds seats, retrying only on lock contention. A retry starts a brand new
     * transaction, so a deadlock loser never returns a half-applied hold.
     */
    public HoldResponse holdSeats(Long userId, Long showId, HoldRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_HOLD_ATTEMPTS; attempt++) {
            try {
                return transactions.createHold(userId, showId, request.seatIds());
            } catch (PessimisticLockingFailureException ex) {
                // Covers CannotAcquireLockException and DeadlockLoserDataAccessException,
                // both of which are subclasses of this type -- catching them separately
                // is redundant and Java rejects it as an illegal multi-catch.
                last = ex;
                log.warn("Lock contention on hold (show {}, attempt {}/{})", showId, attempt, MAX_HOLD_ATTEMPTS);
                sleepBackoff(attempt);
            }
        }
        throw new ApiException(ErrorCode.SEAT_UNAVAILABLE,
                "Seats are being booked by others right now, please try again",
                java.util.Map.of("retryAfterMs", 500));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummary> listBookings(Long userId, Pageable pageable) {
        Page<Booking> page = bookingRepository.findByUser(userId, pageable);
        return PageResponse.of(page, b -> new BookingSummary(
                b.getBookingRef(), b.getStatus().name(), b.getShow().getEvent().getTitle(),
                b.getShow().getStartTime(), bookingItemRepository.findByBookingId(b.getId()).size(),
                b.getTotalAmount(), b.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BookingDetail getBooking(String bookingRef, Long userId, Role role) {
        Booking booking = requireOwnedBooking(bookingRef, userId, role);

        List<String> seats = bookingItemRepository.findByBookingId(booking.getId()).stream()
                .filter(BookingItem::isActive)
                .map(item -> item.getShowSeat().getSeat().label())
                .sorted()
                .toList();

        var show = booking.getShow();
        var hall = show.getHall();
        String qr = booking.getStatus() == BookingStatus.CONFIRMED
                ? qrCodeGenerator.dataUri(booking.getBookingRef())
                : null;

        return new BookingDetail(
                booking.getBookingRef(),
                booking.getStatus().name(),
                new EventInfo(show.getEvent().getId(), show.getEvent().getTitle()),
                new ShowInfo(show.getId(), show.getStartTime(), hall.getName(),
                        hall.getVenue().getName(), hall.getVenue().getCity()),
                seats,
                booking.getTotalAmount(),
                booking.getExpiresAt(),
                qr);
    }

    /** Cancels a confirmed booking and initiates a refund, outside the database transaction. */
    public CancelResponse cancelBooking(String bookingRef, Long userId, Role role) {
        Booking booking = requireOwnedBooking(bookingRef, userId, role);

        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            transactions.cancelPendingBooking(booking.getId());
            return new CancelResponse(BookingStatus.CANCELLED.name(), "NOT_APPLICABLE");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.INVALID_BOOKING_STATE,
                    "Only a confirmed or pending booking can be cancelled");
        }

        Instant cutoff = booking.getShow().getStartTime()
                .minus(Duration.ofHours(props.getBooking().getCancellationCutoffHours()));
        if (Instant.now().isAfter(cutoff)) {
            throw new ApiException(ErrorCode.CANCELLATION_WINDOW_CLOSED,
                    "Bookings cannot be cancelled within "
                            + props.getBooking().getCancellationCutoffHours() + " hours of the show");
        }

        transactions.cancelConfirmedBooking(booking.getId());
        String refundStatus = paymentService.refundBooking(booking.getId());
        return new CancelResponse(BookingStatus.CANCELLED.name(), refundStatus);
    }

    @Transactional(readOnly = true)
    public Booking requireOwnedBooking(String bookingRef, Long userId, Role role) {
        Booking booking = bookingRepository.findDetailByBookingRef(bookingRef)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (role != Role.ADMIN && !booking.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This booking belongs to another account");
        }
        return booking;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(20L * attempt + random.nextInt(30));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
