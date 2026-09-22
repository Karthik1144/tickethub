package com.tickethub.catalogue.controller;

import com.tickethub.catalogue.dto.CatalogueDtos.*;
import com.tickethub.catalogue.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin catalogue")
public class AdminCatalogueController {

    private final CatalogueService catalogueService;

    public AdminCatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @PostMapping("/venues")
    @Operation(summary = "Create a venue")
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.createVenue(request));
    }

    @PostMapping("/venues/{venueId}/halls")
    @Operation(summary = "Create a hall and generate its seat layout")
    public ResponseEntity<HallResponse> createHall(@PathVariable Long venueId,
                                                   @Valid @RequestBody CreateHallRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.createHall(venueId, request));
    }

    @PostMapping("/events")
    @Operation(summary = "Create an event")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.createEvent(request));
    }

    @PutMapping("/events/{eventId}")
    @Operation(summary = "Update an event")
    public EventResponse updateEvent(@PathVariable Long eventId,
                                     @Valid @RequestBody CreateEventRequest request) {
        return catalogueService.updateEvent(eventId, request);
    }

    @DeleteMapping("/events/{eventId}")
    @Operation(summary = "Archive an event")
    public ResponseEntity<Void> archiveEvent(@PathVariable Long eventId) {
        catalogueService.archiveEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shows")
    @Operation(summary = "Schedule a show and generate its seats")
    public ResponseEntity<ShowCreatedResponse> scheduleShow(@Valid @RequestBody CreateShowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.scheduleShow(request));
    }

    @PostMapping("/shows/{showId}/cancel")
    @Operation(summary = "Cancel a show")
    public ResponseEntity<Void> cancelShow(@PathVariable Long showId) {
        catalogueService.cancelShow(showId);
        return ResponseEntity.noContent().build();
    }
}
