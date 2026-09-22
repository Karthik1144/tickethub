package com.tickethub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tickethub")
public class TicketHubProperties {

    private Jwt jwt = new Jwt();
    private Booking booking = new Booking();
    private Scheduler scheduler = new Scheduler();
    private PaymentProps payment = new PaymentProps();
    private Cors cors = new Cors();
    private RateLimit ratelimit = new RateLimit();

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    public PaymentProps getPayment() { return payment; }
    public void setPayment(PaymentProps payment) { this.payment = payment; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public RateLimit getRatelimit() { return ratelimit; }
    public void setRatelimit(RateLimit ratelimit) { this.ratelimit = ratelimit; }

    public static class Jwt {
        private String secret = "change-me-change-me-change-me-change-me";
        private long accessTokenMinutes = 15;
        private long refreshTokenDays = 7;
        private String issuer = "tickethub";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTokenMinutes() { return accessTokenMinutes; }
        public void setAccessTokenMinutes(long v) { this.accessTokenMinutes = v; }
        public long getRefreshTokenDays() { return refreshTokenDays; }
        public void setRefreshTokenDays(long v) { this.refreshTokenDays = v; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
    }

    public static class Booking {
        private int holdMinutes = 5;
        private int maxSeatsPerBooking = 6;
        private int cancellationCutoffHours = 2;

        public int getHoldMinutes() { return holdMinutes; }
        public void setHoldMinutes(int v) { this.holdMinutes = v; }
        public int getMaxSeatsPerBooking() { return maxSeatsPerBooking; }
        public void setMaxSeatsPerBooking(int v) { this.maxSeatsPerBooking = v; }
        public int getCancellationCutoffHours() { return cancellationCutoffHours; }
        public void setCancellationCutoffHours(int v) { this.cancellationCutoffHours = v; }
    }

    public static class Scheduler {
        private String holdExpiryCron = "0/30 * * * * *";
        private int batchSize = 100;

        public String getHoldExpiryCron() { return holdExpiryCron; }
        public void setHoldExpiryCron(String v) { this.holdExpiryCron = v; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int v) { this.batchSize = v; }
    }

    public static class PaymentProps {
        private String gateway = "MOCK";
        private String currency = "INR";
        private String webhookSecret = "test-webhook-secret";

        public String getGateway() { return gateway; }
        public void setGateway(String v) { this.gateway = v; }
        public String getCurrency() { return currency; }
        public void setCurrency(String v) { this.currency = v; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String v) { this.webhookSecret = v; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String v) { this.allowedOrigins = v; }
    }

    public static class RateLimit {
        private int loginPerMinute = 10;
        private int holdPerMinute = 30;

        public int getLoginPerMinute() { return loginPerMinute; }
        public void setLoginPerMinute(int v) { this.loginPerMinute = v; }
        public int getHoldPerMinute() { return holdPerMinute; }
        public void setHoldPerMinute(int v) { this.holdPerMinute = v; }
    }
}
