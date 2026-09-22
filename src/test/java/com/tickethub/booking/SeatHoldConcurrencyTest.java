package com.tickethub.booking;

import com.tickethub.booking.dto.BookingDtos.HoldRequest;
import com.tickethub.booking.dto.BookingDtos.HoldResponse;
import com.tickethub.booking.repository.BookingItemRepository;
import com.tickethub.booking.service.BookingService;
import com.tickethub.catalogue.domain.Show;
import com.tickethub.common.exception.ApiException;
import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.repository.ShowSeatRepository;
import com.tickethub.support.AbstractIntegrationTest;
import com.tickethub.support.TestDataFactory;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-01 to CT-03 and CT-06 from the test plan: the proof that a seat
 * can never be sold twice, no matter how many users race for it.
 */
class SeatHoldConcurrencyTest extends AbstractIntegrationTest {

    @Autowired BookingService bookingService;
    @Autowired ShowSeatRepository showSeatRepository;
    @Autowired BookingItemRepository bookingItemRepository;
    @Autowired TestDataFactory fixtures;

    @Test
    @DisplayName("CT-01: 200 users race for the same seat, exactly one wins")
    void onlyOneUserWinsTheSameSeat() throws Exception {
        Show show = fixtures.showWithSeats(2, 5, new BigDecimal("300.00"));
        Long seatId = fixtures.seatIdsOf(show.getId()).get(0);

        int threads = 200;
        List<User> users = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            users.add(fixtures.randomUser());
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        runInParallel(threads, i -> {
            try {
                bookingService.holdSeats(users.get(i).getId(), show.getId(), new HoldRequest(List.of(seatId)));
                success.incrementAndGet();
            } catch (ApiException ex) {
                conflict.incrementAndGet();
            } catch (RuntimeException ex) {
                unexpected.incrementAndGet();
            }
        });

        assertThat(success.get()).as("exactly one winner").isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
        assertThat(unexpected.get()).as("no unhandled exceptions").isZero();
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();
        assertThat(showSeatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    @DisplayName("CT-02: overlapping seat sets never produce a double booking")
    void overlappingSeatSetsStayConsistent() throws Exception {
        Show show = fixtures.showWithSeats(2, 5, new BigDecimal("250.00"));
        List<Long> pool = fixtures.seatIdsOf(show.getId());

        int threads = 100;
        List<User> users = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            users.add(fixtures.randomUser());
        }

        Random random = new Random(42);
        List<List<Long>> requests = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            List<Long> copy = new ArrayList<>(pool);
            Collections.shuffle(copy, random);
            requests.add(List.copyOf(copy.subList(0, 2 + random.nextInt(3))));
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        runInParallel(threads, i -> {
            try {
                bookingService.holdSeats(users.get(i).getId(), show.getId(), new HoldRequest(requests.get(i)));
                success.incrementAndGet();
            } catch (ApiException ignored) {
                // conflicts are the expected outcome for the losers
            } catch (RuntimeException ex) {
                unexpected.incrementAndGet();
            }
        });

        assertThat(unexpected.get()).isZero();
        assertThat(bookingItemRepository.findDoubleBookedSeatIds()).isEmpty();

        long heldSeats = showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.HELD);
        long activeItems = bookingItemRepository.countActiveItemsForShow(show.getId());
        assertThat(activeItems).as("seat state and booking items agree").isEqualTo(heldSeats);
        assertThat(success.get()).isPositive();
    }

    @Test
    @DisplayName("CT-03: a partially unavailable request holds nothing at all")
    void partialRequestIsAllOrNothing() {
        Show show = fixtures.showWithSeats(1, 5, new BigDecimal("200.00"));
        List<Long> seats = fixtures.seatIdsOf(show.getId());
        User first = fixtures.randomUser();
        User second = fixtures.randomUser();

        HoldResponse held = bookingService.holdSeats(first.getId(), show.getId(),
                new HoldRequest(List.of(seats.get(0))));
        assertThat(held.seats()).hasSize(1);

        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE)).isEqualTo(4);

        try {
            bookingService.holdSeats(second.getId(), show.getId(),
                    new HoldRequest(List.of(seats.get(0), seats.get(1), seats.get(2))));
            throw new AssertionError("expected the hold to be rejected");
        } catch (ApiException expected) {
            assertThat(expected.getProperties()).containsKey("unavailableSeatIds");
        }

        // Nothing from the failed request may have been held.
        assertThat(showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE)).isEqualTo(4);
    }

    private void runInParallel(int threads, java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 64));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        task.accept(index);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(120, TimeUnit.SECONDS)).as("all threads finished").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
