package com.tickethub.seating.dto;

import com.tickethub.seating.domain.SeatStatus;

import java.util.List;

/** Payload pushed to SSE subscribers after a committed seat change. */
public record SeatUpdateEvent(Long showId, List<Long> seatIds, SeatStatus status) {
}
