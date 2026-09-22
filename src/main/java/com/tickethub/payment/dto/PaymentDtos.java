package com.tickethub.payment.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

public final class PaymentDtos {

    private PaymentDtos() {}

    public record StartPaymentResponse(Long paymentId, String gateway, String gatewayOrderId,
                                       BigDecimal amount, String currency,
                                       Map<String, String> checkoutPayload) {}

    /** Shape of the gateway callback body. The signature header is verified before this is trusted. */
    public record WebhookPayload(@NotBlank String eventId,
                                 @NotBlank String type,
                                 @NotBlank String gatewayOrderId,
                                 String gatewayPaymentId) {}

    public record WebhookAck(String status) {}
}
