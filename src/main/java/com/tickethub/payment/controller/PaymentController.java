package com.tickethub.payment.controller;

import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.security.CurrentUser;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.payment.dto.PaymentDtos.*;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookSignatureVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                             WebhookSignatureVerifier signatureVerifier,
                             CurrentUser currentUser,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.signatureVerifier = signatureVerifier;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/bookings/{bookingRef}/payments")
    @Operation(summary = "Create a gateway order for a pending booking")
    public ResponseEntity<StartPaymentResponse> startPayment(@PathVariable String bookingRef) {
        AuthenticatedUser user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.startPayment(bookingRef, user.id(), user.role()));
    }

    /**
     * Gateway callback. The raw body is read as a String so the HMAC is computed over
     * exactly the bytes that were signed, before anything is trusted or parsed.
     */
    @PostMapping(value = "/payments/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Payment gateway webhook (signed)")
    public WebhookAck webhook(@RequestHeader(value = "X-Gateway-Signature", required = false) String signature,
                              @RequestBody String rawBody) {

        if (!signatureVerifier.isValid(rawBody, signature)) {
            throw new ApiException(ErrorCode.INVALID_SIGNATURE, "Webhook signature verification failed");
        }
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Webhook body is not valid JSON");
        }
        if (payload.eventId() == null || payload.type() == null || payload.gatewayOrderId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Webhook body is missing required fields");
        }
        return paymentService.handleWebhook(payload);
    }
}
