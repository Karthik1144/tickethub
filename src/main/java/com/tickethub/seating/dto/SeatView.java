package com.tickethub.seating.dto;

import com.tickethub.catalogue.domain.SeatType;
import com.tickethub.seating.domain.SeatStatus;

import java.math.BigDecimal;

/** Flat projection used by the seat map: no entity loading, no N+1. */
public record SeatView(Long id, String row, short number, SeatType type, SeatStatus status, BigDecimal price) {

    public String label() {
        return row + number;
    }
}
