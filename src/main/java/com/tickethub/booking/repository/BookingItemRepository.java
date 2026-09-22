package com.tickethub.booking.repository;

import com.tickethub.booking.domain.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingId(Long bookingId);

    /** Deactivating frees the generated column, so the seat can be booked again. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE BookingItem bi SET bi.active = false WHERE bi.booking.id = :bookingId AND bi.active = true")
    int deactivateByBookingId(@Param("bookingId") Long bookingId);

    /** Active seats held or booked for one show, counted in SQL (no lazy loading). */
    @Query(value = """
           SELECT COUNT(*) FROM booking_items bi
           JOIN show_seats ss ON ss.id = bi.show_seat_id
           WHERE bi.is_active = 1 AND ss.show_id = :showId
           """, nativeQuery = true)
    long countActiveItemsForShow(@Param("showId") Long showId);

    /** Consistency probe used by tests: must always return an empty list. */
    @Query(value = """
           SELECT active_show_seat_id FROM booking_items
           WHERE is_active = 1
           GROUP BY active_show_seat_id
           HAVING COUNT(*) > 1
           """, nativeQuery = true)
    List<Long> findDoubleBookedSeatIds();
}
