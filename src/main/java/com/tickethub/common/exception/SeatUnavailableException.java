package com.tickethub.common.exception;

import java.util.List;
import java.util.Map;

/** Thrown when one or more requested seats could not be held. Rolls back the whole hold. */
public class SeatUnavailableException extends ApiException {

    public SeatUnavailableException(List<Long> unavailableSeatIds) {
        super(ErrorCode.SEAT_UNAVAILABLE,
              "One or more selected seats are no longer available",
              Map.of("unavailableSeatIds", unavailableSeatIds));
    }
}
