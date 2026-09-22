package com.tickethub.catalogue.controller;

import com.tickethub.catalogue.domain.EventCategory;
import com.tickethub.catalogue.dto.CatalogueDtos.*;
import com.tickethub.catalogue.service.CatalogueService;
import com.tickethub.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Catalogue")
public class EventController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogueService catalogueService;

    public EventController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    @Operation(summary = "Search events")
    public PageResponse<EventResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) EventCategory category,
                                              @RequestParam(required = false) String language,
                                              @RequestParam(required = false) String city,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return catalogueService.searchEvents(q, category, language, city,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Event details with upcoming shows")
    public EventDetailResponse get(@PathVariable Long eventId) {
        return catalogueService.getEvent(eventId);
    }

    @GetMapping("/{eventId}/shows")
    @Operation(summary = "Upcoming shows for an event")
    public List<ShowResponse> shows(@PathVariable Long eventId) {
        return catalogueService.upcomingShows(eventId);
    }
}
