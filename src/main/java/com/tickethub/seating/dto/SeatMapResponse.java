package com.tickethub.seating.dto;

import java.time.Instant;
import java.util.List;

public record SeatMapResponse(Long showId, Instant serverTime, List<SeatView> seats) {
}
