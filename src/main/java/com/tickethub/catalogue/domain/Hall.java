package com.tickethub.catalogue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "halls")
@Getter
@Setter
@NoArgsConstructor
public class Hall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    public Hall(Venue venue, String name, int totalSeats) {
        this.venue = venue;
        this.name = name;
        this.totalSeats = totalSeats;
    }
}
