package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.ReviewRequest;
import com.myorderlynk.app.booking.BookingDtos.ReviewResponse;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Customer reviews of completed bookings (PRD §8 step 10, §13 Review). One review per booking;
 * a service provider's aggregate service rating is derived from visible reviews.
 */
@Service
@Slf4j
public class BookingReviewService {

    private final BookingRepository bookings;
    private final BookingReviewRepository reviews;
    private final BookingNotificationService notifications;
    private final BookingMapper mapper;

    public BookingReviewService(BookingRepository bookings, BookingReviewRepository reviews,
                                BookingNotificationService notifications, BookingMapper mapper) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    /** Submit a review for a booking, authenticated by the signed-in customer or by contact match. */
    @Transactional
    public ReviewResponse submit(String publicBookingId, UUID customerUserId, String contact, ReviewRequest req) {
        Booking b = bookings.findByPublicBookingId(publicBookingId.trim())
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!ownsBooking(b, customerUserId, contact)) {
            throw ApiException.forbidden("You can only review your own booking");
        }
        if (!reviewable(b.getStatus())) {
            throw ApiException.badRequest("You can review a booking once the service is completed");
        }
        if (reviews.existsByBookingId(b.getId())) {
            throw ApiException.badRequest("You have already reviewed this booking");
        }
        BookingReview review = new BookingReview();
        review.setBookingId(b.getId());
        review.setVendorId(b.getVendorId());
        review.setServiceId(b.getServiceId());
        review.setCustomerUserId(customerUserId);
        review.setRating(req.rating());
        review.setComment(req.comment());
        BookingReview saved = reviews.save(review);
        log.info("Review {}★ submitted for booking {} (vendor {})", req.rating(), b.getPublicBookingId(),
                b.getVendorId());
        notifications.notifyProvider(b, "REVIEW_SUBMITTED",
                "New " + req.rating() + "★ review for booking " + b.getPublicBookingId() + ".");
        return mapper.review(saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> forVendor(UUID vendorId) {
        return reviews.findByVendorIdAndVisibleTrueOrderByCreatedAtDesc(vendorId).stream()
                .map(mapper::review).toList();
    }

    private boolean ownsBooking(Booking b, UUID customerUserId, String contact) {
        if (customerUserId != null && customerUserId.equals(b.getCustomerUserId())) {
            return true;
        }
        if (contact == null || contact.isBlank()) {
            return false;
        }
        String needle = contact.trim();
        return needle.equalsIgnoreCase(b.getCustomerPhone())
                || (b.getCustomerEmail() != null && needle.equalsIgnoreCase(b.getCustomerEmail()));
    }

    private boolean reviewable(BookingStatus status) {
        return status == BookingStatus.COMPLETED
                || status == BookingStatus.BALANCE_PENDING
                || status == BookingStatus.CLOSED;
    }
}
