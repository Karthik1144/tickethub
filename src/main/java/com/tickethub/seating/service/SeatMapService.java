package com.tickethub.seating.service;

import com.tickethub.catalogue.repository.ShowRepository;
import com.tickethub.common.exception.ApiException;
import com.tickethub.seating.dto.SeatMapResponse;
import com.tickethub.seating.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SeatMapService {

    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;

    public SeatMapService(ShowSeatRepository showSeatRepository, ShowRepository showRepository) {
        this.showSeatRepository = showSeatRepository;
        this.showRepository = showRepository;
    }

    /** One indexed query, projected straight into DTOs (no entities, no N+1). */
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw ApiException.notFound("Show");
        }
        return new SeatMapResponse(showId, Instant.now(), showSeatRepository.findSeatMap(showId));
    }
}
