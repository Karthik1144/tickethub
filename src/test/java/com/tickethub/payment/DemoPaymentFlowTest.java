package com.tickethub.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.catalogue.domain.Show;
import com.tickethub.support.AbstractIntegrationTest;
import com.tickethub.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs with the demo profile (merged with the inherited "test" profile). */
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoPaymentFlowTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestDataFactory fixtures;

    @Test
    @DisplayName("hold, start payment, simulate the gateway: booking is confirmed with a QR ticket")
    void fullFlowConfirmsBooking() throws Exception {
        Show show = fixtures.showWithSeats(1, 4, new BigDecimal("100.00"));
        List<Long> seats = fixtures.seatIdsOf(show.getId());
        String token = registerAndLogin();

        String holdBody = mockMvc.perform(post("/api/v1/shows/{id}/holds", show.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatIds", seats.subList(0, 2)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = objectMapper.readTree(holdBody).get("bookingRef").asText();

        mockMvc.perform(post("/api/v1/bookings/{ref}/payments", ref)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/dev/bookings/{ref}/simulate-payment", ref)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/bookings/{ref}", ref)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.qrCode").exists());
    }

    @Test
    @DisplayName("another account cannot confirm someone else's booking")
    void simulateIsOwnerOnly() throws Exception {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        List<Long> seats = fixtures.seatIdsOf(show.getId());
        String ownerToken = registerAndLogin();
        String intruderToken = registerAndLogin();

        String holdBody = mockMvc.perform(post("/api/v1/shows/{id}/holds", show.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatIds", seats.subList(0, 1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = objectMapper.readTree(holdBody).get("bookingRef").asText();

        mockMvc.perform(post("/api/v1/dev/bookings/{ref}/simulate-payment", ref)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("simulating before a payment order exists is rejected")
    void simulateRequiresStartedPayment() throws Exception {
        Show show = fixtures.showWithSeats(1, 3, new BigDecimal("100.00"));
        List<Long> seats = fixtures.seatIdsOf(show.getId());
        String token = registerAndLogin();

        String holdBody = mockMvc.perform(post("/api/v1/shows/{id}/holds", show.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("seatIds", seats.subList(0, 1)))))
                .andReturn().getResponse().getContentAsString();
        String ref = objectMapper.readTree(holdBody).get("bookingRef").asText();

        mockMvc.perform(post("/api/v1/dev/bookings/{ref}/simulate-payment", ref)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BOOKING_STATE"));
    }

    private String registerAndLogin() throws Exception {
        String email = "demo-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "Password1!", "fullName", "Demo Tester"))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Password1!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
