package com.tickethub.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Invalid signature"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already registered"),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "Seat unavailable"),
    SHOW_STARTED(HttpStatus.CONFLICT, "Show has already started"),
    SHOW_OVERLAP(HttpStatus.CONFLICT, "Another show overlaps in this hall"),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "Seat hold has expired"),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Booking is already confirmed"),
    INVALID_BOOKING_STATE(HttpStatus.CONFLICT, "Booking is not in a valid state for this action"),
    CANCELLATION_WINDOW_CLOSED(HttpStatus.CONFLICT, "Cancellation window has closed"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Resource was modified concurrently, retry"),
    MAX_SEATS_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Too many seats requested"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
