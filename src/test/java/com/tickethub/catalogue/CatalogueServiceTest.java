package com.tickethub.catalogue;

import com.tickethub.catalogue.domain.EventCategory;
import com.tickethub.catalogue.domain.SeatType;
import com.tickethub.catalogue.dto.CatalogueDtos.*;
import com.tickethub.catalogue.service.CatalogueService;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.repository.ShowSeatRepository;
import com.tickethub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueServiceTest extends AbstractIntegrationTest {

    @Autowired CatalogueService catalogueService;
    @Autowired ShowSeatRepository showSeatRepository;

    @Test
    @DisplayName("TC-CAT-02: scheduling a show generates one AVAILABLE seat per hall seat, priced by type")
    void schedulingGeneratesShowSeats() {
        VenueResponse venue = catalogueService.createVenue(
                new CreateVenueRequest("Arena One", "Vijayawada", "Bandar Road"));
        HallResponse hall = catalogueService.createHall(venue.id(),
                new CreateHallRequest("Hall 1", 3, 10, Map.of("A", SeatType.PREMIUM)));
        EventResponse event = catalogueService.createEvent(new CreateEventRequest(
                "Live Concert", "A show", EventCategory.CONCERT, "English", (short) 90, null));

        ShowCreatedResponse show = catalogueService.scheduleShow(new CreateShowRequest(
                event.id(), hall.id(), Instant.now().plus(Duration.ofDays(1)),
                new BigDecimal("200.00"), Map.of(SeatType.PREMIUM, new BigDecimal("1.5"))));

        assertThat(hall.totalSeats()).isEqualTo(30);
        assertThat(show.seatsGenerated()).isEqualTo(30);
        assertThat(showSeatRepository.countByShowIdAndStatus(show.id(), SeatStatus.AVAILABLE)).isEqualTo(30);

        List<BigDecimal> premiumPrices = showSeatRepository.findSeatMap(show.id()).stream()
                .filter(seat -> seat.type() == SeatType.PREMIUM)
                .map(seat -> seat.price())
                .toList();
        assertThat(premiumPrices).hasSize(10).allMatch(p -> p.compareTo(new BigDecimal("300.00")) == 0);
    }

    @Test
    @DisplayName("TC-CAT-01: an overlapping show in the same hall is rejected")
    void overlappingShowRejected() {
        VenueResponse venue = catalogueService.createVenue(
                new CreateVenueRequest("Arena Two", "Vijayawada", null));
        HallResponse hall = catalogueService.createHall(venue.id(),
                new CreateHallRequest("Hall 2", 2, 5, null));
        EventResponse event = catalogueService.createEvent(new CreateEventRequest(
                "Movie Night", null, EventCategory.MOVIE, "Telugu", (short) 120, null));

        Instant start = Instant.now().plus(Duration.ofDays(2));
        catalogueService.scheduleShow(new CreateShowRequest(
                event.id(), hall.id(), start, new BigDecimal("150.00"), null));

        CreateShowRequest overlapping = new CreateShowRequest(
                event.id(), hall.id(), start.plus(Duration.ofMinutes(30)), new BigDecimal("150.00"), null);

        assertThatThrownBy(() -> catalogueService.scheduleShow(overlapping))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOW_OVERLAP);
    }

    @Test
    @DisplayName("a show cannot be scheduled in the past")
    void pastShowRejected() {
        VenueResponse venue = catalogueService.createVenue(new CreateVenueRequest("Arena Three", "Hyderabad", null));
        HallResponse hall = catalogueService.createHall(venue.id(), new CreateHallRequest("Hall 3", 1, 5, null));
        EventResponse event = catalogueService.createEvent(new CreateEventRequest(
                "Past Event", null, EventCategory.THEATRE, "Hindi", (short) 60, null));

        CreateShowRequest past = new CreateShowRequest(
                event.id(), hall.id(), Instant.now().minus(Duration.ofHours(1)), new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> catalogueService.scheduleShow(past))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("FR-CAT-05: search filters by title and city, and archived events disappear")
    void searchFiltersEvents() {
        VenueResponse venue = catalogueService.createVenue(new CreateVenueRequest("Arena Four", "Chennai", null));
        HallResponse hall = catalogueService.createHall(venue.id(), new CreateHallRequest("Hall 4", 1, 5, null));
        EventResponse event = catalogueService.createEvent(new CreateEventRequest(
                "Unique Jazz Evening", null, EventCategory.CONCERT, "English", (short) 90, null));
        catalogueService.scheduleShow(new CreateShowRequest(
                event.id(), hall.id(), Instant.now().plus(Duration.ofDays(3)), new BigDecimal("400.00"), null));

        var byTitle = catalogueService.searchEvents("Unique Jazz", null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(byTitle.content()).extracting(EventResponse::title).contains("Unique Jazz Evening");

        var byCity = catalogueService.searchEvents(null, null, null, "Chennai",
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(byCity.content()).extracting(EventResponse::id).contains(event.id());

        catalogueService.archiveEvent(event.id());
        var afterArchive = catalogueService.searchEvents("Unique Jazz", null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(afterArchive.content()).isEmpty();
    }
}
