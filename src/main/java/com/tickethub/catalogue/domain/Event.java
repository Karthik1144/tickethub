package com.tickethub.catalogue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(length = 30)
    private String language;

    @Column(name = "duration_min")
    private Short durationMin;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventStatus status = EventStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Event(String title, String description, EventCategory category,
                 String language, Short durationMin, String posterUrl) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.language = language;
        this.durationMin = durationMin;
        this.posterUrl = posterUrl;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
