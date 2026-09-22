package com.tickethub.payment;

import com.tickethub.config.TicketHubProperties;
import com.tickethub.payment.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        TicketHubProperties props = new TicketHubProperties();
        props.getPayment().setWebhookSecret("test-webhook-secret");
        verifier = new WebhookSignatureVerifier(props);
    }

    @Test
    @DisplayName("a correctly signed body is accepted")
    void validSignatureAccepted() {
        String body = "{\"eventId\":\"evt_1\",\"type\":\"payment.success\"}";
        assertThat(verifier.isValid(body, verifier.sign(body))).isTrue();
    }

    @Test
    @DisplayName("TC-BOOK-02: a body changed after signing is rejected")
    void tamperedBodyRejected() {
        String signature = verifier.sign("{\"amount\":\"100.00\"}");
        assertThat(verifier.isValid("{\"amount\":\"1.00\"}", signature)).isFalse();
    }

    @Test
    @DisplayName("a missing or blank signature is rejected")
    void missingSignatureRejected() {
        assertThat(verifier.isValid("{}", null)).isFalse();
        assertThat(verifier.isValid("{}", "  ")).isFalse();
    }
}
