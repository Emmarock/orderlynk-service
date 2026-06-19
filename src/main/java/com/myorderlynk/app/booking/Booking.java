package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer's service appointment (PRD §13 Booking) with its own lifecycle ({@link BookingStatus}).
 * Money fields snapshot the service price, deposit and balance at booking time so later price
 * edits never change a confirmed booking.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_public_id", columnList = "publicBookingId", unique = true),
        @Index(name = "idx_booking_vendor", columnList = "vendorId"),
        @Index(name = "idx_booking_customer", columnList = "customerUserId"),
        @Index(name = "idx_booking_start", columnList = "appointmentStart")
})
public class Booking extends BaseEntity {

    /** Human-friendly id, format SB-YYMMDD-RANDOM. */
    @Column(nullable = false, unique = true)
    private String publicBookingId;

    /** Optional logged-in customer; bookings can be placed as a guest. */
    private UUID customerUserId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    private String customerEmail;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private UUID serviceId;

    /** Service name captured at booking time (survives later catalog edits). */
    @Column(nullable = false)
    private String serviceNameSnapshot;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BookingAddOn> addOns = new ArrayList<>();

    @Column(nullable = false)
    private Instant appointmentStart;

    @Column(nullable = false)
    private Instant appointmentEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalMode approvalMode = ApprovalMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceLocationType locationType = ServiceLocationType.AT_PROVIDER;

    /** Customer address snapshot, only when {@code locationType == CUSTOMER_LOCATION}. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "houseNumber", column = @Column(name = "customer_house_number")),
            @AttributeOverride(name = "street", column = @Column(name = "customer_street")),
            @AttributeOverride(name = "city", column = @Column(name = "customer_city")),
            @AttributeOverride(name = "state", column = @Column(name = "customer_state")),
            @AttributeOverride(name = "postcode", column = @Column(name = "customer_postcode")),
            @AttributeOverride(name = "country", column = @Column(name = "customer_country"))
    })
    private Address serviceAddress = new Address();

    // ---- Money snapshot (PRD §10) ----

    /** Base + add-ons, before tax. */
    @Column(nullable = false)
    private BigDecimal servicePrice = BigDecimal.ZERO;

    /** Travel surcharge snapshot, applied when {@code locationType == CUSTOMER_LOCATION} (0 otherwise). */
    @Column(nullable = false)
    private BigDecimal travelFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** servicePrice + travelFee + taxAmount; the gross amount payable. */
    @Column(nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositType depositType = DepositType.NONE;

    /** Deposit due to confirm the booking (0 when no deposit). */
    @Column(nullable = false)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** Sum of all successful (non-refund) payments recorded against this booking. */
    @Column(nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "CAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** When the temporary slot hold expires for an unpaid deposit booking (PRD §14). */
    private Instant holdExpiresAt;

    /** Most recent reminder sent, so the scheduler doesn't re-send (PRD §11). */
    private Instant lastReminderAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceChannel sourceChannel = SourceChannel.VENDOR_LINK;

    @Column(length = 2000)
    private String notes;

    /** Optional reason captured on reject/cancel. */
    private String statusReason;

    /** Outstanding balance after payments (never negative). */
    public BigDecimal balanceDue() {
        BigDecimal due = totalAmount.subtract(amountPaid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    public void addAddOn(BookingAddOn addOn) {
        addOn.setBooking(this);
        addOns.add(addOn);
    }
}
