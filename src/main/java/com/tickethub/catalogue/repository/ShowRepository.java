package com.tickethub.catalogue.repository;

import com.tickethub.catalogue.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("""
           SELECT s FROM Show s
           JOIN FETCH s.hall h JOIN FETCH h.venue
           WHERE s.event.id = :eventId
             AND s.startTime >= :from
             AND s.status = com.tickethub.catalogue.domain.ShowStatus.SCHEDULED
           ORDER BY s.startTime ASC
           """)
    List<Show> findUpcomingByEvent(@Param("eventId") Long eventId, @Param("from") Instant from);

    @Query("""
           SELECT s FROM Show s
           JOIN FETCH s.event JOIN FETCH s.hall h JOIN FETCH h.venue
           WHERE s.id = :id
           """)
    Optional<Show> findDetailById(@Param("id") Long id);

    /** Overlap guard for scheduling (FR-CAT-03). */
    @Query("""
           SELECT COUNT(s) FROM Show s
           WHERE s.hall.id = :hallId
             AND s.status <> com.tickethub.catalogue.domain.ShowStatus.CANCELLED
             AND s.startTime < :endTime
             AND s.endTime > :startTime
           """)
    long countOverlapping(@Param("hallId") Long hallId,
                          @Param("startTime") Instant startTime,
                          @Param("endTime") Instant endTime);
}
