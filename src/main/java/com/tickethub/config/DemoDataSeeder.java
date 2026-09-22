package com.tickethub.config;

import com.tickethub.catalogue.domain.EventCategory;
import com.tickethub.catalogue.domain.SeatType;
import com.tickethub.catalogue.dto.CatalogueDtos.*;
import com.tickethub.catalogue.service.CatalogueService;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Run with -Dspring.profiles.active=demo to get a browsable catalogue and two logins. */
@Configuration
@Profile("demo")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    @Bean
    public ApplicationRunner seedDemoData(CatalogueService catalogue,
                                          UserRepository userRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByEmail("admin@tickethub.dev")) {
                log.info("Demo data already present, skipping seed");
                return;
            }

            userRepository.save(new User("admin@tickethub.dev",
                    passwordEncoder.encode("Admin123!"), "Demo Admin", null, Role.ADMIN));
            userRepository.save(new User("user@tickethub.dev",
                    passwordEncoder.encode("User1234!"), "Demo User", null, Role.USER));

            VenueResponse venue = catalogue.createVenue(
                    new CreateVenueRequest("Arena One", "Vijayawada", "Bandar Road"));
            HallResponse hall = catalogue.createHall(venue.id(),
                    new CreateHallRequest("Hall 1", 8, 12, Map.of("A", SeatType.PREMIUM, "B", SeatType.PREMIUM)));

            EventResponse concert = catalogue.createEvent(new CreateEventRequest(
                    "Indie Night Live", "An evening of live indie music",
                    EventCategory.CONCERT, "English", (short) 120, null));
            EventResponse movie = catalogue.createEvent(new CreateEventRequest(
                    "The Long Road", "A road movie",
                    EventCategory.MOVIE, "Telugu", (short) 140, null));

            catalogue.scheduleShow(new CreateShowRequest(concert.id(), hall.id(),
                    Instant.now().plus(Duration.ofDays(1)), new BigDecimal("400.00"),
                    Map.of(SeatType.PREMIUM, new BigDecimal("1.5"))));
            catalogue.scheduleShow(new CreateShowRequest(movie.id(), hall.id(),
                    Instant.now().plus(Duration.ofDays(2)), new BigDecimal("250.00"),
                    Map.of(SeatType.PREMIUM, new BigDecimal("1.4"))));

            log.info("Demo data seeded: admin@tickethub.dev / Admin123!, user@tickethub.dev / User1234!");
        };
    }
}
