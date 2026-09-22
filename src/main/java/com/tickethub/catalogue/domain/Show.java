package com.tickethub.catalogue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ShowStatus status = ShowStatus.SCHEDULED;

    public Show(Event event, Hall hall, Instant startTime, Instant endTime, BigDecimal basePrice) {
        this.event = event;
        this.hall = hall;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePrice = basePrice;
    }

    public boolean hasStarted(Instant now) {
        return !startTime.isAfter(now);
    }
}
