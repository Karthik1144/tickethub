package com.tickethub.seating.controller;

import com.tickethub.seating.dto.SeatMapResponse;
import com.tickethub.seating.service.SeatMapService;
import com.tickethub.seating.service.SeatUpdatePublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/shows")
@Tag(name = "Seating")
public class SeatController {

    private final SeatMapService seatMapService;
    private final SeatUpdatePublisher seatUpdatePublisher;

    public SeatController(SeatMapService seatMapService, SeatUpdatePublisher seatUpdatePublisher) {
        this.seatMapService = seatMapService;
        this.seatUpdatePublisher = seatUpdatePublisher;
    }

    @GetMapping("/{showId}/seats")
    @Operation(summary = "Seat map for a show")
    public SeatMapResponse seatMap(@PathVariable Long showId) {
        return seatMapService.seatMap(showId);
    }

    @GetMapping(value = "/{showId}/seats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live seat updates (Server-Sent Events)")
    public SseEmitter stream(@PathVariable Long showId) {
        return seatUpdatePublisher.subscribe(showId);
    }
}
