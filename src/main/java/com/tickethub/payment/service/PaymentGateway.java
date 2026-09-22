package com.tickethub.payment.service;

import java.math.BigDecimal;
import java.util.Map;

/** The gateway seam: Stripe, Razorpay or the mock all sit behind this. */
public interface PaymentGateway {

    String name();

    GatewayOrder createOrder(String bookingRef, BigDecimal amount, String currency, String idempotencyKey);

    void refund(String gatewayPaymentId, BigDecimal amount);

    record GatewayOrder(String orderId, Map<String, String> checkoutPayload) {}
}
