package com.tickethub.booking.service;

/** Result of trying to confirm a booking after a successful payment. */
public enum ConfirmOutcome {
    CONFIRMED,
    ALREADY_CONFIRMED,
    /** Payment arrived after the hold expired and the seats were gone: the caller must refund. */
    EXPIRED_LATE
}
