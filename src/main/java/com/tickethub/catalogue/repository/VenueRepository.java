package com.tickethub.catalogue.repository;

import com.tickethub.catalogue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByCityIgnoreCase(String city);
}
