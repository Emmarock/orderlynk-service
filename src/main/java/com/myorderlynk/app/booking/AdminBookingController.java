package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.BookingResponse;
import com.myorderlynk.app.booking.BookingDtos.CancelRequest;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsAdmin;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin oversight of service bookings (PRD §15: "Admin can view and support service bookings"). */
@RestController
@RequestMapping("/api/admin/bookings")
@IsAdmin
public class AdminBookingController {

    private final BookingService bookings;
    private final CurrentUser currentUser;

    public AdminBookingController(BookingService bookings, CurrentUser currentUser) {
        this.bookings = bookings;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<BookingResponse> all() {
        return bookings.allBookings();
    }

    /** Admin escalation: cancel a booking on the provider's behalf (PRD §14 provider-cancel path). */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest req) {
        AuthPrincipal me = currentUser.require();
        return bookings.cancel(null, id, req == null ? null : req.reason(), "admin:" + me.userId());
    }

    /** Admin close-out of a fully-served booking. */
    @PostMapping("/{id}/close")
    public BookingResponse close(@PathVariable UUID id) {
        AuthPrincipal me = currentUser.require();
        return bookings.close(null, id, "admin:" + me.userId());
    }
}
