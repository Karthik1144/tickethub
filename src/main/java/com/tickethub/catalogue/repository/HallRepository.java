package com.tickethub.catalogue.repository;

import com.tickethub.catalogue.domain.Hall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HallRepository extends JpaRepository<Hall, Long> {
    List<Hall> findByVenueId(Long venueId);
}
