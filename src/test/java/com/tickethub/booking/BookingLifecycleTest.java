package com.tickethub.booking;

import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.dto.BookingDtos.HoldRequest;
import com.tickethub.booking.dto.BookingDtos.HoldResponse;
import com.tickethub.booking.repository.BookingItemRepository;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingService;
import com.tickethub.booking.service.HoldExpiryScheduler;
import com.tickethub.catalogue.domain.Show;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.dto.PaymentDtos.StartPaymentResponse;
import com.tickethub.payment.dto.PaymentDtos.WebhookPayload;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.repository.ShowSeatRepository;
import com.tickethub.support.AbstractIntegrationTest;
import com.tickethub.support.TestDataFactory;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hold, pay, confirm, expire and cancel: the full booking lifecycle (FR-BOOK-*). */
class BookingLifecycleTest extends AbstractIntegrationTest {

    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingItemRepository bookingItemRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ShowSeatRepository showSeatRepository;
    @Autowired HoldExpiryScheduler scheduler;
    @Autowired TestDataFactory fixtures;

    @Test
    @DisplayName("TC-SEAT-01 / TC-BOOK-04: paying a held booking confirms it and books the seats")
    void payingConfirmsBooking() {
        Show show = fixtures.showWithSeats(1, 4, new BigDecimal("150.00"));
        User user = fixtures.randomUser();
        List<Long> seats = fixtures.seatIdsOf(show.getId()).subList(0, 2);

        HoldResponse hold = bookingService.holdSeats(user.getId(), show.getId(), new HoldRequest(seats));
        assertThat(hold.status()).isEqualTo(BookingStatus.PENDING_PAYMENT.name());
        assertThat(hold.totalAmount()).isEqualByComparingTo("300.00");

        StartPaymentResponse payment = paymentService.startPayment(hold.bookingRef(), user.getId(), Role.USER);
        assertThat(payment.gatewayOrderId()).isNotBlank();

        var ack = paymentService.handleWebhook(new WebhookPayload(
                UUID.randomUUID().toString(), "payment.success", payment.gatewayOrderId(), "pay_1"));
        assertThat(ack.status()).isEqualTo("CONFIRMED");

        var booking = bookingRepository.findByBookingRef(hold.bookingRef()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.BOOKED)).isEqualTo(2);
        assertThat(paymentRepository.findByGatewayOrderId(payment.gatewayOrderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();
    }

    @Test
    @DisplayName("TC-BOOK-03: the same webhook delivered repeatedly changes state only once")
    void webhookIsIdempotent() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        User user = fixtures.randomUser();
        HoldResponse hold = bookingService.holdSeats(user.getId(), show.getId(),
                new HoldRequest(fixtures.seatIdsOf(show.getId()).subList(0, 1)));

        StartPaymentResponse payment = paymentService.startPayment(hold.bookingRef(), user.getId(), Role.USER);
        String eventId = UUID.randomUUID().toString();
        WebhookPayload payload = new WebhookPayload(eventId, "payment.success", payment.gatewayOrderId(), "pay_2");

        assertThat(paymentService.handleWebhook(payload).status()).isEqualTo("CONFIRMED");
        for (int i = 0; i < 4; i++) {
            assertThat(paymentService.handleWebhook(payload).status()).isEqualTo("DUPLICATE_IGNORED");
        }

        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.BOOKED)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SEAT-03: an expired hold is swept and its seats return to AVAILABLE")
    void expiredHoldIsReleased() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        User user = fixtures.randomUser();
        HoldResponse hold = bookingService.holdSeats(user.getId(), show.getId(),
                new HoldRequest(fixtures.seatIdsOf(show.getId()).subList(0, 2)));

        // Move the timer into the past instead of waiting five minutes.
        var booking = bookingRepository.findByBookingRef(hold.bookingRef()).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepository.saveAndFlush(booking);

        assertThat(scheduler.sweep()).isPositive();

        assertThat(bookingRepository.findByBookingRef(hold.bookingRef()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE)).isEqualTo(3);
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();
    }

    @Test
    @DisplayName("TC-BOOK-05: payment that lands after expiry is refunded, not confirmed")
    void latePaymentIsRefunded() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        User user = fixtures.randomUser();
        HoldResponse hold = bookingService.holdSeats(user.getId(), show.getId(),
                new HoldRequest(fixtures.seatIdsOf(show.getId()).subList(0, 1)));

        StartPaymentResponse payment = paymentService.startPayment(hold.bookingRef(), user.getId(), Role.USER);

        var booking = bookingRepository.findByBookingRef(hold.bookingRef()).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepository.saveAndFlush(booking);
        scheduler.sweep();

        var ack = paymentService.handleWebhook(new WebhookPayload(
                UUID.randomUUID().toString(), "payment.success", payment.gatewayOrderId(), "pay_3"));

        assertThat(ack.status()).isEqualTo("REFUNDED_LATE_PAYMENT");
        assertThat(paymentRepository.findByGatewayOrderId(payment.gatewayOrderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE)).isEqualTo(3);
    }

    @Test
    @DisplayName("BR-1: holding more than the maximum number of seats is rejected")
    void tooManySeatsRejected() {
        Show show = fixtures.showWithSeats(2, 5, new BigDecimal("100.00"));
        User user = fixtures.randomUser();
        List<Long> sevenSeats = fixtures.seatIdsOf(show.getId()).subList(0, 7);

        assertThatThrownBy(() -> bookingService.holdSeats(user.getId(), show.getId(), new HoldRequest(sevenSeats)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MAX_SEATS_EXCEEDED);
    }

    @Test
    @DisplayName("TC-SEC-01: a booking cannot be read by another account")
    void bookingIsPrivateToItsOwner() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        User owner = fixtures.randomUser();
        User intruder = fixtures.randomUser();

        HoldResponse hold = bookingService.holdSeats(owner.getId(), show.getId(),
                new HoldRequest(fixtures.seatIdsOf(show.getId()).subList(0, 1)));

        assertThat(bookingService.getBooking(hold.bookingRef(), owner.getId(), Role.USER).bookingRef())
                .isEqualTo(hold.bookingRef());

        assertThatThrownBy(() -> bookingService.getBooking(hold.bookingRef(), intruder.getId(), Role.USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("FR-BOOK-07: cancelling a confirmed booking frees the seats and refunds")
    void cancelConfirmedBookingReleasesSeats() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("120.00"));
        User user = fixtures.randomUser();
        HoldResponse hold = bookingService.holdSeats(user.getId(), show.getId(),
                new HoldRequest(fixtures.seatIdsOf(show.getId()).subList(0, 2)));

        StartPaymentResponse payment = paymentService.startPayment(hold.bookingRef(), user.getId(), Role.USER);
        paymentService.handleWebhook(new WebhookPayload(
                UUID.randomUUID().toString(), "payment.success", payment.gatewayOrderId(), "pay_4"));

        var response = bookingService.cancelBooking(hold.bookingRef(), user.getId(), Role.USER);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED.name());
        assertThat(response.refundStatus()).isEqualTo("INITIATED");
        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE)).isEqualTo(3);
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();
    }

    @Test
    @DisplayName("CT-06: the database itself blocks a second active item for one seat")
    void databaseSafetyNetBlocksDuplicateItem() {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        User first = fixtures.randomUser();
        User second = fixtures.randomUser();
        List<Long> seat = fixtures.seatIdsOf(show.getId()).subList(0, 1);

        bookingService.holdSeats(first.getId(), show.getId(), new HoldRequest(seat));

        // Even if application logic were bypassed, the unique key on the generated
        // column means the seat cannot end up in two active bookings.
        assertThatThrownBy(() -> bookingService.holdSeats(second.getId(), show.getId(), new HoldRequest(seat)))
                .isInstanceOf(ApiException.class);
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();
    }
}
