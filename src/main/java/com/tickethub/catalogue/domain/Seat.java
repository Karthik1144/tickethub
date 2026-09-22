package com.tickethub.catalogue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private short seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 10)
    private SeatType seatType = SeatType.STANDARD;

    public Seat(Hall hall, String rowLabel, short seatNumber, SeatType seatType) {
        this.hall = hall;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
    }

    public String label() {
        return rowLabel + seatNumber;
    }
}
