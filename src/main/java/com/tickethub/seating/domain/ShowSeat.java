package com.tickethub.seating.domain;

import com.tickethub.catalogue.domain.Seat;
import com.tickethub.catalogue.domain.Show;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "show_seats")
@Getter
@Setter
@NoArgsConstructor
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "held_by_booking_id")
    private Long heldByBookingId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    @Column(nullable = false)
    private int version;

    public ShowSeat(Show show, Seat seat, BigDecimal price) {
        this.show = show;
        this.seat = seat;
        this.price = price;
    }
}
