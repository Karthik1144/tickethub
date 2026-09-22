package com.tickethub.booking.domain;

import com.tickethub.seating.domain.ShowSeat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One seat inside a booking. The database also carries a generated column
 * (active_show_seat_id) with a unique index: the duplicate-booking safety net.
 * That column is deliberately not mapped here, it is a database-only guard.
 */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id", nullable = false)
    private ShowSeat showSeat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public BookingItem(Booking booking, ShowSeat showSeat, BigDecimal price) {
        this.booking = booking;
        this.showSeat = showSeat;
        this.price = price;
    }
}
