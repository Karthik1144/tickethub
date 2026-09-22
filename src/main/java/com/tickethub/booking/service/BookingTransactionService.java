package com.tickethub.booking.service;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingItem;
import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.dto.BookingDtos.*;
import com.tickethub.booking.repository.BookingItemRepository;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.catalogue.domain.Show;
import com.tickethub.catalogue.domain.ShowStatus;
import com.tickethub.catalogue.repository.ShowRepository;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.common.exception.SeatUnavailableException;
import com.tickethub.config.TicketHubProperties;
import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.domain.ShowSeat;
import com.tickethub.seating.dto.SeatUpdateEvent;
import com.tickethub.seating.repository.ShowSeatRepository;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every write that must be atomic lives here, in its own transaction.
 * Kept separate from BookingService so retries (deadlocks) start a fresh transaction,
 * and so no external call is ever made while database locks are held.
 */
@Service
public class BookingTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BookingTransactionService.class);

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final BookingReferenceGenerator referenceGenerator;
    private final TicketHubProperties props;
    private final ApplicationEventPublisher events;

    public BookingTransactionService(BookingRepository bookingRepository,
                                     BookingItemRepository bookingItemRepository,
                                     ShowSeatRepository showSeatRepository,
                                     ShowRepository showRepository,
                                     UserRepository userRepository,
                                     BookingReferenceGenerator referenceGenerator,
                                     TicketHubProperties props,
                                     ApplicationEventPublisher events) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.showSeatRepository = showSeatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.props = props;
        this.events = events;
    }

    /**
     * All-or-nothing seat hold (FR-SEAT-04, FR-SEAT-06).
     * The guard is the single conditional UPDATE: if it does not change exactly as many
     * rows as seats requested, the whole transaction rolls back and nothing is held.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HoldResponse createHold(Long userId, Long showId, List<Long> requestedSeatIds) {

        List<Long> seatIds = requestedSeatIds.stream().distinct().sorted().toList();
        int max = props.getBooking().getMaxSeatsPerBooking();
        if (seatIds.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Select at least one seat");
        }
        if (seatIds.size() > max) {
            throw new ApiException(ErrorCode.MAX_SEATS_EXCEEDED,
                    "You can book at most " + max + " seats in one booking");
        }

        Instant now = Instant.now();
        Show show = showRepository.findDetailById(showId).orElseThrow(() -> ApiException.notFound("Show"));
        if (show.getStatus() == ShowStatus.CANCELLED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This show has been cancelled");
        }
        if (show.hasStarted(now)) {
            throw new ApiException(ErrorCode.SHOW_STARTED, "This show has already started");
        }

        List<ShowSeat> seats = showSeatRepository.findByShowIdAndIdIn(showId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "One or more seats do not belong to this show");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Instant expiresAt = now.plus(Duration.ofMinutes(props.getBooking().getHoldMinutes()));

        Booking booking = new Booking(referenceGenerator.generate(), user, show, expiresAt);
        bookingRepository.saveAndFlush(booking);

        int updated = showSeatRepository.holdSeats(showId, seatIds, booking.getId(), expiresAt);
        if (updated != seatIds.size()) {
            Set<Long> held = new HashSet<>(showSeatRepository.findSeatIdsHeldBy(booking.getId()));
            List<Long> unavailable = seatIds.stream().filter(id -> !held.contains(id)).toList();
            log.debug("Hold rejected for show {}: {} of {} seats taken", showId, unavailable.size(), seatIds.size());
            throw new SeatUnavailableException(unavailable);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<BookingItem> items = new ArrayList<>(seats.size());
        for (ShowSeat seat : seats) {
            items.add(new BookingItem(booking, seat, seat.getPrice()));
            total = total.add(seat.getPrice());
        }
        bookingItemRepository.saveAll(items);

        booking.setTotalAmount(total);
        bookingRepository.save(booking);

        events.publishEvent(new SeatUpdateEvent(showId, seatIds, SeatStatus.HELD));

        List<SeatLine> lines = seats.stream()
                .sorted(Comparator.comparing((ShowSeat s) -> s.getSeat().getRowLabel())
                        .thenComparing(s -> s.getSeat().getSeatNumber()))
                .map(s -> new SeatLine(s.getId(), s.getSeat().label(), s.getPrice()))
                .toList();

        return new HoldResponse(booking.getBookingRef(), booking.getStatus().name(), showId,
                lines, total, booking.getExpiresAt());
    }

    /** Confirms a booking after a successful payment (FR-BOOK-04). Idempotent. */
    @Transactional
    public ConfirmOutcome confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking"));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return ConfirmOutcome.ALREADY_CONFIRMED;
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return ConfirmOutcome.EXPIRED_LATE;
        }

        int expectedSeats = bookingItemRepository.findByBookingId(bookingId).size();
        int confirmed = showSeatRepository.confirmHeldSeats(bookingId);

        if (confirmed != expectedSeats || expectedSeats == 0) {
            // The hold lapsed and the seats went elsewhere: release anything left and refund upstream.
            log.warn("Late payment for booking {}: {} of {} seats still held", bookingId, confirmed, expectedSeats);
            releaseAndClose(booking, BookingStatus.EXPIRED);
            return ConfirmOutcome.EXPIRED_LATE;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        events.publishEvent(new SeatUpdateEvent(booking.getShow().getId(),
                showSeatRepository.findSeatIdsHeldBy(bookingId), SeatStatus.BOOKED));
        return ConfirmOutcome.CONFIRMED;
    }

    /** Releases an expired hold (FR-SEAT-05). Safe to call repeatedly. */
    @Transactional
    public boolean expireBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        if (!booking.isExpired(Instant.now())) {
            return false;
        }
        List<Long> seatIds = showSeatRepository.findSeatIdsHeldBy(bookingId);
        releaseAndClose(booking, BookingStatus.EXPIRED);
        publishAvailable(booking, seatIds);
        return true;
    }

    /** Cancels a booking that is still pending payment (user-initiated or show cancelled). */
    @Transactional
    public void cancelPendingBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ApiException(ErrorCode.INVALID_BOOKING_STATE, "Booking is no longer pending payment");
        }
        List<Long> seatIds = showSeatRepository.findSeatIdsHeldBy(bookingId);
        releaseAndClose(booking, BookingStatus.CANCELLED);
        publishAvailable(booking, seatIds);
    }

    /** Cancels a confirmed booking and frees its seats (FR-BOOK-07). */
    @Transactional
    public void cancelConfirmedBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking"));

        List<Long> seatIds = showSeatRepository.findSeatIdsHeldBy(bookingId);
        showSeatRepository.releaseBookedSeats(bookingId);
        showSeatRepository.releaseHeldSeats(bookingId);
        bookingItemRepository.deactivateByBookingId(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        publishAvailable(booking, seatIds);
    }

    private void releaseAndClose(Booking booking, BookingStatus finalStatus) {
        showSeatRepository.releaseHeldSeats(booking.getId());
        bookingItemRepository.deactivateByBookingId(booking.getId());
        booking.setStatus(finalStatus);
        bookingRepository.save(booking);
    }

    private void publishAvailable(Booking booking, List<Long> seatIds) {
        if (!seatIds.isEmpty()) {
            events.publishEvent(new SeatUpdateEvent(booking.getShow().getId(), seatIds, SeatStatus.AVAILABLE));
        }
    }
}
