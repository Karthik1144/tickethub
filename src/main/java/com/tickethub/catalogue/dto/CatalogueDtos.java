package com.tickethub.catalogue.dto;

import com.tickethub.catalogue.domain.EventCategory;
import com.tickethub.catalogue.domain.SeatType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CatalogueDtos {

    private CatalogueDtos() {}

    // ----- requests -----
    public record CreateVenueRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 255) String address) {}

    /** rows x seatsPerRow generates the seat layout; seatTypeByRow is optional. */
    public record CreateHallRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(26) int rows,
            @Min(1) @Max(60) int seatsPerRow,
            Map<String, SeatType> seatTypeByRow) {}

    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull EventCategory category,
            @Size(max = 30) String language,
            @Min(1) @Max(1000) Short durationMin,
            @Size(max = 500) String posterUrl) {}

    public record CreateShowRequest(
            @NotNull Long eventId,
            @NotNull Long hallId,
            @NotNull Instant startTime,
            @NotNull @DecimalMin("0.0") BigDecimal basePrice,
            Map<SeatType, BigDecimal> priceMultipliers) {}

    // ----- responses -----
    public record VenueResponse(Long id, String name, String city, String address) {}

    public record HallResponse(Long id, Long venueId, String name, int totalSeats) {}

    public record EventResponse(Long id, String title, String description, String category,
                                String language, Short durationMin, String posterUrl) {}

    public record ShowResponse(Long id, Long eventId, String eventTitle, String venueName,
                               String city, String hallName, Instant startTime, Instant endTime,
                               BigDecimal basePrice, String status) {}

    public record ShowCreatedResponse(Long id, int seatsGenerated) {}

    public record EventDetailResponse(EventResponse event, List<ShowResponse> shows) {}
}
