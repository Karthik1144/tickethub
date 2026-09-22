package com.tickethub.report.repository;

import com.tickethub.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReportRepository extends JpaRepository<Booking, Long> {

    /** Confirmed sales grouped by event. Returns [eventId, title, bookings, seats, revenue]. */
    @Query(value = """
           SELECT e.id            AS event_id,
                  e.title         AS event_title,
                  COUNT(DISTINCT b.id) AS bookings,
                  COUNT(bi.id)    AS seats,
                  COALESCE(SUM(bi.price), 0) AS revenue
           FROM bookings b
           JOIN shows s          ON s.id = b.show_id
           JOIN events e         ON e.id = s.event_id
           JOIN booking_items bi ON bi.booking_id = b.id AND bi.is_active = 1
           WHERE b.status = 'CONFIRMED'
             AND b.created_at >= :from AND b.created_at < :to
           GROUP BY e.id, e.title
           ORDER BY revenue DESC
           """, nativeQuery = true)
    List<Object[]> salesByEvent(@Param("from") Instant from, @Param("to") Instant to);
}
