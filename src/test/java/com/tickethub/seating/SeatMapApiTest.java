package com.tickethub.seating;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.dto.BookingDtos.HoldRequest;
import com.tickethub.booking.service.BookingService;
import com.tickethub.catalogue.domain.Show;
import com.tickethub.support.AbstractIntegrationTest;
import com.tickethub.support.TestDataFactory;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SeatMapApiTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookingService bookingService;
    @Autowired TestDataFactory fixtures;

    @Test
    @DisplayName("FR-SEAT-01: the seat map is public and reflects held seats")
    void seatMapIsPublicAndCurrent() throws Exception {
        Show show = fixtures.showWithSeats(1, 4, new BigDecimal("100.00"));

        mockMvc.perform(get("/api/v1/shows/{id}/seats", show.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(4))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"));

        User user = fixtures.randomUser();
        List<Long> firstSeat = fixtures.seatIdsOf(show.getId()).subList(0, 1);
        bookingService.holdSeats(user.getId(), show.getId(), new HoldRequest(firstSeat));

        mockMvc.perform(get("/api/v1/shows/{id}/seats", show.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].status").value("HELD"));
    }

    @Test
    @DisplayName("an unknown show returns 404 in the standard error shape")
    void unknownShowReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/shows/{id}/seats", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-SEAT-02: holding an already held seat returns 409 with the seat ids")
    void holdConflictReturns409() throws Exception {
        Show show = fixtures.showWithSeats(1, 4, new BigDecimal("100.00"));
        List<Long> seats = fixtures.seatIdsOf(show.getId());

        User owner = fixtures.randomUser();
        bookingService.holdSeats(owner.getId(), show.getId(), new HoldRequest(List.of(seats.get(0))));

        String email = "seatmap-" + java.util.UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email, "password", "Password1!", "fullName", "Seat Tester"))));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Password1!"))))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginBody).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/shows/{id}/holds", show.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("seatIds", List.of(seats.get(0), seats.get(1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableSeatIds").isArray());
    }
}
