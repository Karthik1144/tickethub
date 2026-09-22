package com.tickethub.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Deduplication log: the unique key on event_id makes webhook handling idempotent. */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public WebhookEvent(String eventId) {
        this.eventId = eventId;
    }

    @PrePersist
    void onCreate() {
        this.receivedAt = Instant.now();
    }
}
