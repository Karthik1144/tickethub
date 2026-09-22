package com.tickethub.support;

import com.tickethub.catalogue.domain.*;
import com.tickethub.catalogue.repository.*;
import com.tickethub.seating.domain.ShowSeat;
import com.tickethub.seating.repository.ShowSeatRepository;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds consistent fixtures so every test starts from a known, valid state. */
@Component
public class TestDataFactory {

    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(UserRepository userRepository, VenueRepository venueRepository,
                           HallRepository hallRepository, SeatRepository seatRepository,
                           EventRepository eventRepository, ShowRepository showRepository,
                           ShowSeatRepository showSeatRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User user(String email, Role role) {
        return userRepository.save(new User(email, passwordEncoder.encode("Password1!"),
                "Test " + role.name(), null, role));
    }

    public User randomUser() {
        return user("user-" + UUID.randomUUID() + "@example.com", Role.USER);
    }

    /** A show in a fresh hall with rows x seatsPerRow AVAILABLE seats, starting in 2 hours. */
    @Transactional
    public Show showWithSeats(int rows, int seatsPerRow, BigDecimal price) {
        Venue venue = venueRepository.save(new Venue("Arena " + UUID.randomUUID(), "Vijayawada", "Main road"));
        Hall hall = hallRepository.save(new Hall(venue, "Hall-" + UUID.randomUUID(), rows * seatsPerRow));

        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            for (int n = 1; n <= seatsPerRow; n++) {
                seats.add(new Seat(hall, rowLabel, (short) n, SeatType.STANDARD));
            }
        }
        seatRepository.saveAll(seats);

        Event event = eventRepository.save(new Event("Event " + UUID.randomUUID(), "desc",
                EventCategory.CONCERT, "English", (short) 120, null));

        Instant start = Instant.now().plus(Duration.ofHours(2));
        Show show = showRepository.save(new Show(event, hall, start, start.plus(Duration.ofHours(3)), price));

        List<ShowSeat> showSeats = seats.stream().map(s -> new ShowSeat(show, s, price)).toList();
        showSeatRepository.saveAll(showSeats);
        return show;
    }

    @Transactional
    public List<Long> seatIdsOf(Long showId) {
        return showSeatRepository.findSeatMap(showId).stream()
                .map(view -> view.id())
                .toList();
    }
}
