package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.BookingAddOnResponse;
import com.myorderlynk.app.booking.BookingDtos.BookingResponse;
import com.myorderlynk.app.booking.BookingDtos.PaymentResponse;
import com.myorderlynk.app.booking.BookingDtos.ReviewResponse;
import com.myorderlynk.app.booking.ServiceDtos.AddOnResponse;
import com.myorderlynk.app.booking.ServiceDtos.AvailabilityRuleResponse;
import com.myorderlynk.app.booking.ServiceDtos.BlockedSlotResponse;
import com.myorderlynk.app.booking.ServiceDtos.ProfileResponse;
import com.myorderlynk.app.booking.ServiceDtos.ServiceResponse;
import com.myorderlynk.app.common.Address;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps Services/Bookings entities to API response records. */
@Component
public class BookingMapper {

    private static Address orEmpty(Address a) {
        return a == null ? new Address() : a;
    }

    public ProfileResponse profile(ServiceProviderProfile p) {
        return new ProfileResponse(
                p.getId(), p.getVendorId(), p.isServiceEnabled(), p.getBio(), p.getServiceArea(),
                p.getLocationType(), p.getCustomerLocationFee(), p.getApprovalMode(), p.getCancellationPolicy(),
                p.getDepositPolicy(), p.getBusinessHoursSummary(), p.getLeadTimeHours(), p.getBufferMinutes(),
                p.getMaxAdvanceDays(), p.getDefaultCapacity(), p.getSlotHoldMinutes(), p.getTimezone());
    }

    public AddOnResponse addOn(ServiceAddOn a) {
        return new AddOnResponse(a.getId(), a.getServiceId(), a.getName(), a.getPriceDelta(),
                a.getDurationDelta(), a.isRequired(), a.getMaxSelection(), a.isActive());
    }

    public ServiceResponse service(ServiceOffering s, List<ServiceAddOn> addOns) {
        List<AddOnResponse> addOnResponses = addOns == null ? List.of()
                : addOns.stream().map(this::addOn).toList();
        return new ServiceResponse(
                s.getId(), s.getVendorId(), s.getName(), s.getCategory(), s.getDescription(),
                s.getBasePrice(), s.getCurrency(), s.getDurationMinutes(), s.getImageUrl(),
                s.getLocationType(), s.getCustomerLocationFee(),
                s.getDepositType(), s.getDepositValue(), s.depositFor(s.getBasePrice()),
                s.getTaxRate(), s.isActive(), addOnResponses);
    }

    public AvailabilityRuleResponse rule(AvailabilityRule r) {
        return new AvailabilityRuleResponse(r.getId(), r.getVendorId(), r.getDayOfWeek(),
                r.getStartTime(), r.getEndTime(), r.getCapacity(), r.getBufferMinutes(),
                r.getLeadTimeHours(), r.isActive());
    }

    public BlockedSlotResponse blocked(BlockedSlot b) {
        return new BlockedSlotResponse(b.getId(), b.getVendorId(), b.getStartDatetime(),
                b.getEndDatetime(), b.getReason());
    }

    public BookingAddOnResponse bookingAddOn(BookingAddOn a) {
        return new BookingAddOnResponse(a.getAddOnId(), a.getNameSnapshot(), a.getPriceDelta(),
                a.getDurationDelta(), a.getQuantity());
    }

    public ReviewResponse review(BookingReview r) {
        if (r == null) {
            return null;
        }
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getVendorId(), r.getServiceId(),
                r.getRating(), r.getComment(), r.getCreatedAt());
    }

    public PaymentResponse payment(BookingPayment p) {
        return new PaymentResponse(p.getId(), p.getBookingId(), p.getPaymentType(), p.getAmount(),
                p.getStatus(), p.getMethod(), p.getTransactionReference(), p.getPaidAt());
    }

    public BookingResponse booking(Booking b, String vendorName, BookingReview review) {
        return booking(b, vendorName, review, null, null);
    }

    public BookingResponse booking(Booking b, String vendorName, BookingReview review,
                                   String clientSecret, String paymentReference) {
        Address a = orEmpty(b.getServiceAddress());
        List<BookingAddOnResponse> addOns = b.getAddOns().stream().map(this::bookingAddOn).toList();
        return new BookingResponse(
                b.getId(), b.getPublicBookingId(), b.getCustomerUserId(), b.getCustomerName(),
                b.getCustomerPhone(), b.getCustomerEmail(), b.getVendorId(), vendorName,
                b.getServiceId(), b.getServiceNameSnapshot(), addOns,
                b.getAppointmentStart(), b.getAppointmentEnd(), b.getStatus(), b.getApprovalMode(),
                b.getLocationType(), a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(),
                a.getPostcode(), a.getCountry(),
                b.getServicePrice(), b.getTravelFee(), b.getTaxAmount(), b.getTotalAmount(), b.getDepositType(),
                b.getDepositAmount(), b.getAmountPaid(), b.balanceDue(), b.getRefundedAmount(),
                b.getCurrency(), b.getPaymentStatus(), b.getHoldExpiresAt(), b.getSourceChannel(),
                b.getNotes(), b.getStatusReason(), b.getCreatedAt(), review(review),
                clientSecret, paymentReference);
    }
}
