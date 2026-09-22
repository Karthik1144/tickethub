package com.tickethub.catalogue.service;

import com.tickethub.catalogue.domain.*;
import com.tickethub.catalogue.dto.CatalogueDtos.*;
import com.tickethub.catalogue.repository.*;
import com.tickethub.common.dto.PageResponse;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.seating.domain.ShowSeat;
import com.tickethub.seating.repository.ShowSeatRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CatalogueService {

    private static final int DEFAULT_DURATION_MIN = 120;
    private static final int GAP_MINUTES = 30;

    private final VenueRepository venueRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public CatalogueService(VenueRepository venueRepository,
                            HallRepository hallRepository,
                            SeatRepository seatRepository,
                            EventRepository eventRepository,
                            ShowRepository showRepository,
                            ShowSeatRepository showSeatRepository) {
        this.venueRepository = venueRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    // ---------- admin: venues and halls ----------

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = venueRepository.save(new Venue(request.name(), request.city(), request.address()));
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress());
    }

    /** Creates a hall and generates its seat layout in one transaction (batched inserts). */
    @Transactional
    public HallResponse createHall(Long venueId, CreateHallRequest request) {
        Venue venue = venueRepository.findById(venueId).orElseThrow(() -> ApiException.notFound("Venue"));

        int total = request.rows() * request.seatsPerRow();
        Hall hall = hallRepository.save(new Hall(venue, request.name(), total));

        Map<String, SeatType> typeByRow = request.seatTypeByRow() == null ? Map.of() : request.seatTypeByRow();
        List<Seat> seats = new ArrayList<>(total);
        for (int r = 0; r < request.rows(); r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            SeatType type = typeByRow.getOrDefault(rowLabel, SeatType.STANDARD);
            for (int n = 1; n <= request.seatsPerRow(); n++) {
                seats.add(new Seat(hall, rowLabel, (short) n, type));
            }
        }
        seatRepository.saveAll(seats);
        return new HallResponse(hall.getId(), venue.getId(), hall.getName(), hall.getTotalSeats());
    }

    // ---------- admin: events ----------

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = eventRepository.save(new Event(request.title(), request.description(),
                request.category(), request.language(), request.durationMin(), request.posterUrl()));
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, CreateEventRequest request) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setLanguage(request.language());
        event.setDurationMin(request.durationMin());
        event.setPosterUrl(request.posterUrl());
        return toEventResponse(eventRepository.save(event));
    }

    @Transactional
    public void archiveEvent(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        event.setStatus(EventStatus.ARCHIVED);
        eventRepository.save(event);
    }

    // ---------- admin: shows ----------

    /**
     * Schedules a show and generates its ShowSeat rows.
     * Overlapping shows in the same hall are rejected (FR-CAT-03).
     */
    @Transactional
    public ShowCreatedResponse scheduleShow(CreateShowRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ApiException.notFound("Event"));
        Hall hall = hallRepository.findById(request.hallId())
                .orElseThrow(() -> ApiException.notFound("Hall"));

        if (!request.startTime().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Show start time must be in the future");
        }

        int duration = event.getDurationMin() == null ? DEFAULT_DURATION_MIN : event.getDurationMin();
        Instant endTime = request.startTime().plus(Duration.ofMinutes(duration + GAP_MINUTES));

        if (showRepository.countOverlapping(hall.getId(), request.startTime(), endTime) > 0) {
            throw new ApiException(ErrorCode.SHOW_OVERLAP,
                    "Another show is already scheduled in this hall at that time");
        }

        Show show = showRepository.save(new Show(event, hall, request.startTime(), endTime, request.basePrice()));

        Map<SeatType, BigDecimal> multipliers = request.priceMultipliers() == null
                ? Map.of() : request.priceMultipliers();

        List<Seat> seats = seatRepository.findByHallIdOrderByRowLabelAscSeatNumberAsc(hall.getId());
        List<ShowSeat> showSeats = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            BigDecimal multiplier = multipliers.getOrDefault(seat.getSeatType(), BigDecimal.ONE);
            BigDecimal price = request.basePrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            showSeats.add(new ShowSeat(show, seat, price));
        }
        showSeatRepository.saveAll(showSeats);

        return new ShowCreatedResponse(show.getId(), showSeats.size());
    }

    @Transactional
    public void cancelShow(Long showId) {
        Show show = showRepository.findById(showId).orElseThrow(() -> ApiException.notFound("Show"));
        show.setStatus(ShowStatus.CANCELLED);
        showRepository.save(show);
    }

    // ---------- public reads ----------

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchEvents(String q, EventCategory category,
                                                    String language, String city, Pageable pageable) {
        return PageResponse.of(
                eventRepository.search(blankToNull(q), category, blankToNull(language), blankToNull(city), pageable),
                this::toEventResponse);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        return new EventDetailResponse(toEventResponse(event), upcomingShows(eventId));
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> upcomingShows(Long eventId) {
        return showRepository.findUpcomingByEvent(eventId, Instant.now()).stream()
                .map(this::toShowResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long showId) {
        return toShowResponse(showRepository.findDetailById(showId)
                .orElseThrow(() -> ApiException.notFound("Show")));
    }

    private EventResponse toEventResponse(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getCategory().name(),
                e.getLanguage(), e.getDurationMin(), e.getPosterUrl());
    }

    private ShowResponse toShowResponse(Show s) {
        Hall hall = s.getHall();
        Venue venue = hall.getVenue();
        return new ShowResponse(s.getId(), s.getEvent().getId(), s.getEvent().getTitle(),
                venue.getName(), venue.getCity(), hall.getName(),
                s.getStartTime(), s.getEndTime(), s.getBasePrice(), s.getStatus().name());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
