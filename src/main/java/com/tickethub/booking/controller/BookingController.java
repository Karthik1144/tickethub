package com.tickethub.booking.controller;

import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.security.CurrentUser;
import com.tickethub.booking.dto.BookingDtos.*;
import com.tickethub.booking.service.BookingService;
import com.tickethub.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Bookings")
public class BookingController {

    private static final int MAX_PAGE_SIZE = 50;

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @PostMapping("/shows/{showId}/holds")
    @Operation(summary = "Hold seats and create a pending booking")
    public ResponseEntity<HoldResponse> hold(@PathVariable Long showId,
                                             @Valid @RequestBody HoldRequest request) {
        Long userId = currentUser.requireId();
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.holdSeats(userId, showId, request));
    }

    @GetMapping("/bookings")
    @Operation(summary = "My bookings")
    public PageResponse<BookingSummary> myBookings(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return bookingService.listBookings(currentUser.requireId(),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/bookings/{bookingRef}")
    @Operation(summary = "Booking detail and e-ticket")
    public BookingDetail booking(@PathVariable String bookingRef) {
        AuthenticatedUser user = currentUser.require();
        return bookingService.getBooking(bookingRef, user.id(), user.role());
    }

    @PostMapping("/bookings/{bookingRef}/cancel")
    @Operation(summary = "Cancel a booking and release its seats")
    public CancelResponse cancel(@PathVariable String bookingRef) {
        AuthenticatedUser user = currentUser.require();
        return bookingService.cancelBooking(bookingRef, user.id(), user.role());
    }
}
