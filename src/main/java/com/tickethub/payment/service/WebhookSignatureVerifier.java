package com.tickethub.payment.service;

import com.tickethub.config.TicketHubProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 over the raw request body, compared in constant time. */
@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final TicketHubProperties props;

    public WebhookSignatureVerifier(TicketHubProperties props) {
        this.props = props;
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    props.getPayment().getWebhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute webhook signature", ex);
        }
    }

    public boolean isValid(String rawBody, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(rawBody).getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8));
    }
}
