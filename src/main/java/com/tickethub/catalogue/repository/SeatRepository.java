package com.tickethub.catalogue.repository;

import com.tickethub.catalogue.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByHallIdOrderByRowLabelAscSeatNumberAsc(Long hallId);
    long countByHallId(Long hallId);
}
