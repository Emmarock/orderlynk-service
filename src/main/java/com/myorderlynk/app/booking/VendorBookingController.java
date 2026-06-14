package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.BookingResponse;
import com.myorderlynk.app.booking.BookingDtos.CancelRequest;
import com.myorderlynk.app.booking.BookingDtos.PaymentRequest;
import com.myorderlynk.app.booking.BookingDtos.PaymentResponse;
import com.myorderlynk.app.booking.BookingDtos.RejectRequest;
import com.myorderlynk.app.booking.BookingDtos.RescheduleRequest;
import com.myorderlynk.app.booking.BookingDtos.ReviewResponse;
import com.myorderlynk.app.booking.ServiceDtos.AddOnRequest;
import com.myorderlynk.app.booking.ServiceDtos.AddOnResponse;
import com.myorderlynk.app.booking.ServiceDtos.AvailabilityRuleRequest;
import com.myorderlynk.app.booking.ServiceDtos.AvailabilityRuleResponse;
import com.myorderlynk.app.booking.ServiceDtos.BlockedSlotRequest;
import com.myorderlynk.app.booking.ServiceDtos.BlockedSlotResponse;
import com.myorderlynk.app.booking.ServiceDtos.ImageUploadResponse;
import com.myorderlynk.app.booking.ServiceDtos.ProfileRequest;
import com.myorderlynk.app.booking.ServiceDtos.ProfileResponse;
import com.myorderlynk.app.booking.ServiceDtos.ServiceRequest;
import com.myorderlynk.app.booking.ServiceDtos.ServiceResponse;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsVendor;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Vendor portal endpoints for the Services/Bookings module (PRD §12.3): provider profile,
 * service catalog, add-ons, availability config, and the booking dashboard with its operations.
 */
@RestController
@RequestMapping("/api/vendor")
@IsVendor
public class VendorBookingController {

    private final ServiceCatalogService catalog;
    private final BookingService bookings;
    private final BookingReviewService reviewService;
    private final CurrentUser currentUser;

    public VendorBookingController(ServiceCatalogService catalog, BookingService bookings,
                                   BookingReviewService reviewService, CurrentUser currentUser) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    // ---- Provider profile / Services enablement ----

    @GetMapping("/service-profile")
    public ProfileResponse profile() {
        return catalog.getOrCreateProfile(vendorId());
    }

    @PutMapping("/service-profile")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileRequest req) {
        return catalog.updateProfile(vendorId(), req);
    }

    // ---- Services ----

    @GetMapping("/services")
    public PageResponse<ServiceResponse> services(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return catalog.listServices(vendorId(), page, size);
    }

    @GetMapping("/services/{id}")
    public ServiceResponse service(@PathVariable UUID id) {
        return catalog.getService(vendorId(), id);
    }

    @PostMapping("/services")
    public ServiceResponse createService(@Valid @RequestBody ServiceRequest req) {
        return catalog.createService(vendorId(), req);
    }

    /** Upload a service image from the vendor's device; returns the public URL to store as imageUrl. */
    @PostMapping(value = "/services/image", consumes = "multipart/form-data")
    public ImageUploadResponse uploadServiceImage(@RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(catalog.uploadServiceImage(vendorId(), file));
    }

    @PutMapping("/services/{id}")
    public ServiceResponse updateService(@PathVariable UUID id, @Valid @RequestBody ServiceRequest req) {
        return catalog.updateService(vendorId(), id, req);
    }

    @PatchMapping("/services/{id}/active")
    public ServiceResponse toggleService(@PathVariable UUID id, @RequestParam boolean active) {
        return catalog.toggleService(vendorId(), id, active);
    }

    @DeleteMapping("/services/{id}")
    public void deleteService(@PathVariable UUID id) {
        catalog.deleteService(vendorId(), id);
    }

    // ---- Add-ons ----

    @PostMapping("/services/{serviceId}/add-ons")
    public AddOnResponse addAddOn(@PathVariable UUID serviceId, @Valid @RequestBody AddOnRequest req) {
        return catalog.addAddOn(vendorId(), serviceId, req);
    }

    @PutMapping("/services/{serviceId}/add-ons/{addOnId}")
    public AddOnResponse updateAddOn(@PathVariable UUID serviceId, @PathVariable UUID addOnId,
                                     @Valid @RequestBody AddOnRequest req) {
        return catalog.updateAddOn(vendorId(), serviceId, addOnId, req);
    }

    @DeleteMapping("/services/{serviceId}/add-ons/{addOnId}")
    public void deleteAddOn(@PathVariable UUID serviceId, @PathVariable UUID addOnId) {
        catalog.deleteAddOn(vendorId(), serviceId, addOnId);
    }

    // ---- Availability ----

    @GetMapping("/availability")
    public List<AvailabilityRuleResponse> rules() {
        return catalog.listRules(vendorId());
    }

    @PostMapping("/availability")
    public AvailabilityRuleResponse addRule(@Valid @RequestBody AvailabilityRuleRequest req) {
        return catalog.addRule(vendorId(), req);
    }

    @PutMapping("/availability/{id}")
    public AvailabilityRuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody AvailabilityRuleRequest req) {
        return catalog.updateRule(vendorId(), id, req);
    }

    @DeleteMapping("/availability/{id}")
    public void deleteRule(@PathVariable UUID id) {
        catalog.deleteRule(vendorId(), id);
    }

    // ---- Blocked slots ----

    @GetMapping("/blocked-slots")
    public List<BlockedSlotResponse> blocked() {
        return catalog.listBlocked(vendorId());
    }

    @PostMapping("/blocked-slots")
    public BlockedSlotResponse addBlocked(@Valid @RequestBody BlockedSlotRequest req) {
        return catalog.addBlocked(vendorId(), req);
    }

    @DeleteMapping("/blocked-slots/{id}")
    public void deleteBlocked(@PathVariable UUID id) {
        catalog.deleteBlocked(vendorId(), id);
    }

    // ---- Booking dashboard ----

    @GetMapping("/bookings")
    public PageResponse<BookingResponse> bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return bookings.vendorBookings(vendorId(), start, end, page, size);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse booking(@PathVariable UUID id) {
        return bookings.getForVendor(vendorId(), id);
    }

    @PostMapping("/bookings/{id}/approve")
    public BookingResponse approve(@PathVariable UUID id) {
        return bookings.approve(vendorId(), id, actor());
    }

    @PostMapping("/bookings/{id}/reject")
    public BookingResponse reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectRequest req) {
        return bookings.reject(vendorId(), id, req == null ? null : req.reason(), actor());
    }

    @PostMapping("/bookings/{id}/reschedule")
    public BookingResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest req) {
        return bookings.reschedule(vendorId(), id, req.appointmentStart(), actor());
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest req) {
        return bookings.cancel(vendorId(), id, req == null ? null : req.reason(), actor());
    }

    @PostMapping("/bookings/{id}/start")
    public BookingResponse start(@PathVariable UUID id) {
        return bookings.start(vendorId(), id, actor());
    }

    @PostMapping("/bookings/{id}/complete")
    public BookingResponse complete(@PathVariable UUID id) {
        return bookings.complete(vendorId(), id, actor());
    }

    @PostMapping("/bookings/{id}/no-show")
    public BookingResponse noShow(@PathVariable UUID id) {
        return bookings.noShow(vendorId(), id, actor());
    }

    @PostMapping("/bookings/{id}/close")
    public BookingResponse close(@PathVariable UUID id) {
        return bookings.close(vendorId(), id, actor());
    }

    // ---- Booking payments ----

    @GetMapping("/bookings/{id}/payments")
    public List<PaymentResponse> payments(@PathVariable UUID id) {
        return bookings.payments(vendorId(), id);
    }

    @PostMapping("/bookings/{id}/payments")
    public PaymentResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest req) {
        return bookings.recordPayment(vendorId(), id, req, actor());
    }

    // ---- Reviews ----

    @GetMapping("/reviews")
    public List<ReviewResponse> reviews() {
        return reviewService.forVendor(vendorId());
    }

    private UUID vendorId() {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is linked to your account");
        }
        return vendorId;
    }

    private String actor() {
        AuthPrincipal me = currentUser.require();
        return "user:" + me.userId();
    }
}
