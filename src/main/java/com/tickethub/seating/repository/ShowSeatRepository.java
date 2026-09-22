package com.tickethub.seating.repository;

import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.domain.ShowSeat;
import com.tickethub.seating.dto.SeatView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * Seat map for one show: a single indexed query returning a projection,
     * so no entities and no N+1 (idx_showseat_show_status).
     */
    @Query("""
           SELECT new com.tickethub.seating.dto.SeatView(
                    ss.id, s.rowLabel, s.seatNumber, s.seatType, ss.status, ss.price)
           FROM ShowSeat ss JOIN ss.seat s
           WHERE ss.show.id = :showId
           ORDER BY s.rowLabel ASC, s.seatNumber ASC
           """)
    List<SeatView> findSeatMap(@Param("showId") Long showId);

    /**
     * Primary duplicate-reservation guard. A single atomic conditional UPDATE:
     * InnoDB takes the row locks, and only rows still AVAILABLE are changed.
     * The caller compares the affected row count with the number of seats requested.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           UPDATE show_seats
           SET status = 'HELD', held_by_booking_id = :bookingId,
               hold_expires_at = :expiresAt, version = version + 1
           WHERE show_id = :showId AND id IN (:seatIds) AND status = 'AVAILABLE'
           """, nativeQuery = true)
    int holdSeats(@Param("showId") Long showId,
                  @Param("seatIds") Collection<Long> seatIds,
                  @Param("bookingId") Long bookingId,
                  @Param("expiresAt") Instant expiresAt);

    /** Releases every seat currently held by a booking. Idempotent. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           UPDATE show_seats
           SET status = 'AVAILABLE', held_by_booking_id = NULL,
               hold_expires_at = NULL, version = version + 1
           WHERE held_by_booking_id = :bookingId AND status = 'HELD'
           """, nativeQuery = true)
    int releaseHeldSeats(@Param("bookingId") Long bookingId);

    /** Confirms held seats after payment success. Returns the number of seats moved to BOOKED. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           UPDATE show_seats
           SET status = 'BOOKED', hold_expires_at = NULL, version = version + 1
           WHERE held_by_booking_id = :bookingId AND status = 'HELD'
           """, nativeQuery = true)
    int confirmHeldSeats(@Param("bookingId") Long bookingId);

    /** Releases seats of a cancelled confirmed booking. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           UPDATE show_seats
           SET status = 'AVAILABLE', held_by_booking_id = NULL,
               hold_expires_at = NULL, version = version + 1
           WHERE held_by_booking_id = :bookingId AND status = 'BOOKED'
           """, nativeQuery = true)
    int releaseBookedSeats(@Param("bookingId") Long bookingId);

    @Query("SELECT ss.id FROM ShowSeat ss WHERE ss.heldByBookingId = :bookingId")
    List<Long> findSeatIdsHeldBy(@Param("bookingId") Long bookingId);

    @Query("""
           SELECT new com.tickethub.seating.dto.SeatView(
                    ss.id, s.rowLabel, s.seatNumber, s.seatType, ss.status, ss.price)
           FROM ShowSeat ss JOIN ss.seat s
           WHERE ss.id IN :seatIds
           ORDER BY s.rowLabel ASC, s.seatNumber ASC
           """)
    List<SeatView> findSeatViews(@Param("seatIds") Collection<Long> seatIds);

    List<ShowSeat> findByShowIdAndIdIn(Long showId, Collection<Long> ids);

    long countByShowIdAndStatus(Long showId, SeatStatus status);
}
