package com.tickethub.booking.service;

import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.config.TicketHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Releases holds whose timer ran out (FR-SEAT-05).
 * Idempotent, batched, and safe to run on several instances thanks to SKIP LOCKED.
 */
@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingTransactionService transactions;
    private final TicketHubProperties props;

    public HoldExpiryScheduler(BookingRepository bookingRepository,
                               BookingTransactionService transactions,
                               TicketHubProperties props) {
        this.bookingRepository = bookingRepository;
        this.transactions = transactions;
        this.props = props;
    }

    @Scheduled(cron = "${tickethub.scheduler.hold-expiry-cron}")
    public void releaseExpiredHolds() {
        int released = sweep();
        if (released > 0) {
            log.info("Released {} expired holds", released);
        }
    }

    /** Exposed so tests can trigger one sweep without waiting for the cron. */
    public int sweep() {
        List<Long> expired = bookingRepository.findExpiredPendingIds(
                Instant.now(), props.getScheduler().getBatchSize());

        int released = 0;
        for (Long bookingId : expired) {
            try {
                if (transactions.expireBooking(bookingId)) {
                    released++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire booking {}", bookingId, ex);
            }
        }
        return released;
    }
}
