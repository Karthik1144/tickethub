package com.tickethub.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {}

    public record HoldRequest(@NotNull @NotEmpty List<Long> seatIds) {}

    public record SeatLine(Long id, String label, BigDecimal price) {}

    public record HoldResponse(String bookingRef, String status, Long showId,
                               List<SeatLine> seats, BigDecimal totalAmount, Instant expiresAt) {}

    public record BookingSummary(String bookingRef, String status, String eventTitle,
                                 Instant showStartTime, int seatCount,
                                 BigDecimal totalAmount, Instant createdAt) {}

    public record ShowInfo(Long id, Instant startTime, String hallName, String venueName, String city) {}

    public record EventInfo(Long id, String title) {}

    public record BookingDetail(String bookingRef, String status, EventInfo event, ShowInfo show,
                                List<String> seats, BigDecimal totalAmount,
                                Instant expiresAt, String qrCode) {}

    public record CancelResponse(String status, String refundStatus) {}
}
