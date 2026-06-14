package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.BookingRequest;
import com.myorderlynk.app.booking.BookingDtos.BookingResponse;
import com.myorderlynk.app.booking.BookingDtos.PayRequest;
import com.myorderlynk.app.booking.BookingDtos.PaymentInitResponse;
import com.myorderlynk.app.booking.BookingDtos.ReviewRequest;
import com.myorderlynk.app.booking.BookingDtos.ReviewResponse;
import com.myorderlynk.app.booking.ServiceDtos.DayAvailabilityResponse;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsAuthenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer-facing booking endpoints (PRD §6). Creating a booking, viewing availability, tracking
 * and reviewing are public (guest booking, mirroring guest checkout); the history endpoint
 * requires a customer login.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final AvailabilityService availability;
    private final BookingReviewService reviewService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, AvailabilityService availability,
                             BookingReviewService reviewService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.availability = availability;
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    /** Bookable slots for a service on a date (YYYY-MM-DD, in the provider's timezone). */
    @GetMapping("/availability")
    public DayAvailabilityResponse availability(
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.availableSlots(serviceId, date);
    }

    @PostMapping
    public BookingResponse create(@Valid @RequestBody BookingRequest req) {
        AuthPrincipal principal = currentUser.resolve();
        UUID customerUserId = principal == null ? null : principal.userId();
        return bookingService.create(req, customerUserId);
    }

    @PostMapping("/track")
    public BookingResponse track(@Valid @RequestBody TrackBookingRequest req) {
        return bookingService.track(req.publicBookingId(), req.contact());
    }

    /** Start a Stripe card payment for the booking's outstanding deposit or balance. */
    @PostMapping("/{publicBookingId}/pay")
    public PaymentInitResponse pay(@PathVariable String publicBookingId,
                                   @RequestBody(required = false) PayRequest req) {
        AuthPrincipal principal = currentUser.resolve();
        UUID customerUserId = principal == null ? null : principal.userId();
        return bookingService.initiatePayment(publicBookingId, customerUserId, req == null ? null : req.contact());
    }

    @GetMapping("/mine")
    @IsAuthenticated
    public PageResponse<BookingResponse> myBookings(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return bookingService.customerBookings(currentUser.require().userId(), page, size);
    }

    /** Submit a review after completion. Authenticated by login, or by contact for guests. */
    @PostMapping("/{publicBookingId}/review")
    public ReviewResponse review(@PathVariable String publicBookingId,
                                 @RequestParam(required = false) String contact,
                                 @Valid @RequestBody ReviewRequest req) {
        AuthPrincipal principal = currentUser.resolve();
        UUID customerUserId = principal == null ? null : principal.userId();
        return reviewService.submit(publicBookingId, customerUserId, contact, req);
    }

    public record TrackBookingRequest(@NotBlank String publicBookingId, @NotBlank String contact) {
    }
}
