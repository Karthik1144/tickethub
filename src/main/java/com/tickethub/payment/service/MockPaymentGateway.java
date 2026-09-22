package com.tickethub.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Sandbox gateway used for local development and tests.
 * It creates an order id; payment success arrives through the normal signed webhook.
 */
@Component
@ConditionalOnProperty(name = "tickethub.payment.gateway", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public PaymentGateway.GatewayOrder createOrder(String bookingRef, BigDecimal amount, String currency, String idempotencyKey) {
        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        log.info("Mock gateway created order {} for booking {} ({} {})", orderId, bookingRef, amount, currency);
        return new PaymentGateway.GatewayOrder(orderId, Map.of(
                "gateway", "MOCK",
                "orderId", orderId,
                "bookingRef", bookingRef,
                "amount", amount.toPlainString(),
                "currency", currency));
    }

    @Override
    public void refund(String gatewayPaymentId, BigDecimal amount) {
        log.info("Mock gateway refunded {} for payment {}", amount, gatewayPaymentId);
    }
}
