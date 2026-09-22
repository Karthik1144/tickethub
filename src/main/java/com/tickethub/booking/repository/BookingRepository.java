package com.tickethub.booking.repository;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingRef(String bookingRef);

    @Query("""
           SELECT b FROM Booking b
           JOIN FETCH b.show s JOIN FETCH s.event
           WHERE b.bookingRef = :ref
           """)
    Optional<Booking> findDetailByBookingRef(@Param("ref") String ref);

    @Query(value = """
           SELECT b FROM Booking b JOIN FETCH b.show s JOIN FETCH s.event
           WHERE b.user.id = :userId
           """,
           countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.user.id = :userId")
    Page<Booking> findByUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Expiry sweeper. SKIP LOCKED (MySQL 8) lets several instances run the job
     * at the same time without blocking each other.
     */
    @Query(value = """
           SELECT id FROM bookings
           WHERE status = 'PENDING_PAYMENT' AND expires_at < :now
           ORDER BY expires_at
           LIMIT :batchSize
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<Long> findExpiredPendingIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByShowIdAndStatus(Long showId, BookingStatus status);

    List<Booking> findByShowIdAndStatus(Long showId, BookingStatus status);
}
