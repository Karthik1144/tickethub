package com.tickethub.catalogue.repository;

import com.tickethub.catalogue.domain.Event;
import com.tickethub.catalogue.domain.EventCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Catalogue search. Every filter is optional, and the query only touches
     * ACTIVE events so archived rows never reach the public API.
     */
    @Query("""
           SELECT DISTINCT e FROM Event e
           WHERE e.status = com.tickethub.catalogue.domain.EventStatus.ACTIVE
             AND (:q IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')))
             AND (:category IS NULL OR e.category = :category)
             AND (:language IS NULL OR e.language = :language)
             AND (:city IS NULL OR EXISTS (
                    SELECT 1 FROM Show s WHERE s.event = e
                      AND LOWER(s.hall.venue.city) = LOWER(:city)))
           """)
    Page<Event> search(@Param("q") String q,
                       @Param("category") EventCategory category,
                       @Param("language") String language,
                       @Param("city") String city,
                       Pageable pageable);
}
