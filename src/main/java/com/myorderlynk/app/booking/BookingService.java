package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.BookingRequest;
import com.myorderlynk.app.booking.BookingDtos.BookingResponse;
import com.myorderlynk.app.booking.BookingDtos.PaymentRequest;
import com.myorderlynk.app.booking.BookingDtos.PaymentResponse;
import com.myorderlynk.app.booking.BookingDtos.PaymentInitResponse;
import com.myorderlynk.app.booking.BookingDtos.SelectedAddOn;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.payment.PaymentClient;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentResponse;
import com.myorderlynk.app.payment.PaymentServiceProperties;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.common.AuditService;
import com.myorderlynk.app.common.CodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the booking lifecycle (PRD §6–§8, §10): customer requests, provider
 * approve/reject/reschedule/cancel/complete/no-show, manual payment tracking, and the
 * status transitions and notifications around each step.
 */
@Service
@Slf4j
public class BookingService {

    private static final String AUDIT_TYPE = "BOOKING";

    private final BookingRepository bookings;
    private final ServiceOfferingRepository services;
    private final ServiceAddOnRepository addOns;
    private final ServiceProviderProfileRepository profiles;
    private final BookingPaymentRepository payments;
    private final BookingReviewRepository reviews;
    private final VendorRepository vendors;
    private final AvailabilityService availability;
    private final BookingNotificationService notifications;
    private final AuditService audit;
    private final BookingMapper mapper;
    private final PaymentClient paymentClient;
    private final PaymentServiceProperties paymentProps;

    public BookingService(BookingRepository bookings, ServiceOfferingRepository services,
                          ServiceAddOnRepository addOns, ServiceProviderProfileRepository profiles,
                          BookingPaymentRepository payments, BookingReviewRepository reviews,
                          VendorRepository vendors, AvailabilityService availability,
                          BookingNotificationService notifications, AuditService audit, BookingMapper mapper,
                          PaymentClient paymentClient, PaymentServiceProperties paymentProps) {
        this.bookings = bookings;
        this.services = services;
        this.addOns = addOns;
        this.profiles = profiles;
        this.payments = payments;
        this.reviews = reviews;
        this.vendors = vendors;
        this.availability = availability;
        this.notifications = notifications;
        this.audit = audit;
        this.mapper = mapper;
        this.paymentClient = paymentClient;
        this.paymentProps = paymentProps;
    }

    // ===================== Customer flow =====================

    @Transactional
    public BookingResponse create(BookingRequest req, UUID customerUserId) {
        Vendor vendor = approvedVendor(req.vendorId());
        ServiceProviderProfile profile = profiles.findByVendorId(vendor.getId())
                .filter(ServiceProviderProfile::isServiceEnabled)
                .orElseThrow(() -> ApiException.badRequest("This provider is not accepting bookings"));
        ServiceOffering service = services.findById(req.serviceId())
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!service.getVendorId().equals(vendor.getId()) || !service.isActive()) {
            throw ApiException.badRequest("That service is not available from this provider");
        }

        Booking booking = new Booking();
        booking.setVendorId(vendor.getId());
        booking.setServiceId(service.getId());
        booking.setServiceNameSnapshot(service.getName());
        booking.setCustomerUserId(customerUserId);
        booking.setCustomerName(req.customerName());
        booking.setCustomerPhone(req.customerPhone());
        booking.setCustomerEmail(req.customerEmail());
        booking.setCurrency(service.getCurrency());
        booking.setLocationType(profile.getLocationType());
        booking.setApprovalMode(profile.getApprovalMode());
        booking.setSourceChannel(req.sourceChannel() == null ? SourceChannel.VENDOR_LINK : req.sourceChannel());
        booking.setNotes(req.notes());

        if (profile.getLocationType() == ServiceLocationType.CUSTOMER_LOCATION) {
            Address addr = new Address(req.customerHouseNumber(), req.customerStreet(), req.customerCity(),
                    req.customerState(), req.customerPostcode(), req.customerCountry());
            if (addr.isEmpty()) {
                throw ApiException.badRequest("This is a mobile service — please provide your address");
            }
            booking.setServiceAddress(addr);
        }

        // Price + duration from base service plus required and selected add-ons.
        BigDecimal price = service.getBasePrice();
        int durationMinutes = service.getDurationMinutes();
        Map<UUID, Integer> selected = selectionMap(req.addOns());
        for (ServiceAddOn addOn : addOns.findByServiceIdAndActiveTrue(service.getId())) {
            int qty = addOn.isRequired() ? Math.max(1, selected.getOrDefault(addOn.getId(), 1))
                    : selected.getOrDefault(addOn.getId(), 0);
            if (qty <= 0) {
                continue;
            }
            if (qty > addOn.getMaxSelection()) {
                throw ApiException.badRequest("Too many of add-on '" + addOn.getName() + "' selected");
            }
            BigDecimal lineDelta = addOn.getPriceDelta().multiply(BigDecimal.valueOf(qty));
            price = price.add(lineDelta);
            durationMinutes += addOn.getDurationDelta() * qty;

            BookingAddOn line = new BookingAddOn();
            line.setAddOnId(addOn.getId());
            line.setNameSnapshot(addOn.getName());
            line.setPriceDelta(addOn.getPriceDelta());
            line.setDurationDelta(addOn.getDurationDelta());
            line.setQuantity(qty);
            booking.addAddOn(line);
        }

        Instant start = req.appointmentStart();
        Instant end = start.plus(Duration.ofMinutes(durationMinutes));
        availability.assertBookable(profile, vendor.getId(), start, end);
        booking.setAppointmentStart(start);
        booking.setAppointmentEnd(end);

        BigDecimal tax = price.multiply(service.getTaxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = price.add(tax);
        booking.setServicePrice(price.setScale(2, RoundingMode.HALF_UP));
        booking.setTaxAmount(tax);
        booking.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        booking.setDepositType(service.getDepositType());
        booking.setDepositAmount(depositFor(service.getDepositType(), service.getDepositValue(), total));
        booking.setPaymentStatus(PaymentStatus.PENDING);

        // Initial status: manual → REQUESTED; auto → DEPOSIT_PENDING (if deposit) or CONFIRMED.
        boolean depositRequired = booking.getDepositAmount().signum() > 0;
        if (profile.getApprovalMode() == ApprovalMode.AUTO) {
            if (depositRequired) {
                booking.setStatus(BookingStatus.DEPOSIT_PENDING);
                booking.setHoldExpiresAt(Instant.now().plus(Duration.ofMinutes(profile.getSlotHoldMinutes())));
            } else {
                booking.setStatus(BookingStatus.CONFIRMED);
            }
        } else {
            booking.setStatus(BookingStatus.REQUESTED);
        }
        booking.setPublicBookingId(generateBookingId());

        bookings.save(booking);
        log.info("Booking {} created: vendor={} service={} start={} status={} total={} deposit={}",
                booking.getPublicBookingId(), vendor.getId(), service.getId(), start,
                booking.getStatus(), booking.getTotalAmount(), booking.getDepositAmount());

        audit.logChange(booking.getId(), AUDIT_TYPE, null, booking.getStatus().name(), "SYSTEM", "Booking created");
        notifyOnCreate(booking, vendor);

        // Auto-approved + deposit due → kick off a Stripe card payment so the customer can pay
        // the deposit inline and lock the slot. Best-effort: a payment-service outage never
        // blocks the booking (the customer can pay later from the tracking page).
        CreatePaymentResponse payment = booking.getStatus() == BookingStatus.DEPOSIT_PENDING
                ? initiateStripe(booking, vendor, booking.getDepositAmount(), "deposit")
                : null;
        return response(booking, vendor.getBusinessName(), payment);
    }

    private void notifyOnCreate(Booking booking, Vendor vendor) {
        switch (booking.getStatus()) {
            case REQUESTED -> {
                notifications.notifyProvider(booking, "BOOKING_REQUESTED",
                        "New booking request " + booking.getPublicBookingId() + " for "
                                + booking.getServiceNameSnapshot() + " — awaiting your approval.");
                notifications.notifyCustomer(booking, "BOOKING_RECEIVED",
                        "Hi " + booking.getCustomerName() + ", your request to book "
                                + booking.getServiceNameSnapshot() + " with " + vendor.getBusinessName()
                                + " has been received. You'll be notified when it's approved.");
            }
            case DEPOSIT_PENDING -> {
                notifications.notifyProvider(booking, "BOOKING_AUTO_APPROVED",
                        "Booking " + booking.getPublicBookingId() + " auto-approved; awaiting deposit.");
                notifications.notifyCustomer(booking, "DEPOSIT_REQUIRED",
                        "Your booking " + booking.getPublicBookingId() + " is approved. Please pay the "
                                + money(booking.getDepositAmount(), booking) + " deposit to lock your time.");
            }
            case CONFIRMED -> {
                notifications.notifyProvider(booking, "BOOKING_CONFIRMED",
                        "Booking " + booking.getPublicBookingId() + " is confirmed.");
                notifications.notifyCustomer(booking, "BOOKING_CONFIRMED",
                        "Your booking " + booking.getPublicBookingId() + " with " + vendor.getBusinessName()
                                + " is confirmed. See you then!");
            }
            default -> { /* no-op */ }
        }
    }

    // ===================== Provider operations =====================

    @Transactional
    public BookingResponse approve(UUID vendorId, UUID bookingId, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.REQUESTED);
        boolean depositRequired = b.getDepositAmount().signum() > 0 && b.getAmountPaid().compareTo(b.getDepositAmount()) < 0;
        BookingStatus to = depositRequired ? BookingStatus.DEPOSIT_PENDING : BookingStatus.CONFIRMED;
        if (depositRequired) {
            ServiceProviderProfile p = profiles.findByVendorId(vendorId).orElse(null);
            int hold = p == null ? 15 : p.getSlotHoldMinutes();
            b.setHoldExpiresAt(Instant.now().plus(Duration.ofMinutes(hold)));
        }
        transition(b, to, actor, "Approved");
        if (to == BookingStatus.DEPOSIT_PENDING) {
            notifications.notifyCustomer(b, "BOOKING_APPROVED",
                    "Your booking " + b.getPublicBookingId() + " is approved. Please pay the "
                            + money(b.getDepositAmount(), b) + " deposit to confirm your time.");
        } else {
            notifications.notifyCustomer(b, "BOOKING_CONFIRMED",
                    "Your booking " + b.getPublicBookingId() + " is approved and confirmed.");
        }
        return response(b);
    }

    @Transactional
    public BookingResponse reject(UUID vendorId, UUID bookingId, String reason, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.REQUESTED, BookingStatus.APPROVED, BookingStatus.DEPOSIT_PENDING);
        b.setStatusReason(reason);
        b.setHoldExpiresAt(null);
        transition(b, BookingStatus.REJECTED, actor, reason);
        notifications.notifyCustomer(b, "BOOKING_REJECTED",
                "Your booking " + b.getPublicBookingId() + " was declined"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason));
        return response(b);
    }

    @Transactional
    public BookingResponse reschedule(UUID vendorId, UUID bookingId, Instant newStart, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.REQUESTED, BookingStatus.APPROVED, BookingStatus.DEPOSIT_PENDING,
                BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT);
        ServiceProviderProfile profile = profiles.findByVendorId(vendorId)
                .orElseThrow(() -> ApiException.badRequest("Availability is not configured"));
        long minutes = Duration.between(b.getAppointmentStart(), b.getAppointmentEnd()).toMinutes();
        Instant newEnd = newStart.plus(Duration.ofMinutes(minutes));
        availability.assertBookable(profile, vendorId, newStart, newEnd);
        b.setAppointmentStart(newStart);
        b.setAppointmentEnd(newEnd);
        b.setLastReminderAt(null);
        if (b.getStatus() == BookingStatus.REMINDER_SENT) {
            b.setStatus(BookingStatus.CONFIRMED);
        }
        bookings.save(b);
        audit.logChange(b.getId(), AUDIT_TYPE, b.getStatus().name(), b.getStatus().name(), actor,
                "Rescheduled to " + newStart);
        notifications.notifyCustomer(b, "BOOKING_RESCHEDULED",
                "Your booking " + b.getPublicBookingId() + " has been moved to a new time.");
        return response(b);
    }

    @Transactional
    public BookingResponse cancel(UUID vendorId, UUID bookingId, String reason, String actor) {
        Booking b = vendorId == null ? bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found")) : owned(vendorId, bookingId);
        if (isTerminal(b.getStatus())) {
            throw ApiException.badRequest("This booking can no longer be cancelled");
        }
        b.setStatusReason(reason);
        b.setHoldExpiresAt(null);
        transition(b, BookingStatus.CANCELLED, actor, reason);
        notifications.notifyCustomer(b, "BOOKING_CANCELLED",
                "Your booking " + b.getPublicBookingId() + " has been cancelled"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason));
        notifications.notifyProvider(b, "BOOKING_CANCELLED",
                "Booking " + b.getPublicBookingId() + " was cancelled.");
        return response(b);
    }

    @Transactional
    public BookingResponse start(UUID vendorId, UUID bookingId, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT);
        transition(b, BookingStatus.IN_PROGRESS, actor, null);
        return response(b);
    }

    @Transactional
    public BookingResponse complete(UUID vendorId, UUID bookingId, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT, BookingStatus.IN_PROGRESS);
        boolean balanceDue = b.balanceDue().signum() > 0;
        transition(b, balanceDue ? BookingStatus.BALANCE_PENDING : BookingStatus.COMPLETED, actor, null);
        if (balanceDue) {
            notifications.notifyCustomer(b, "SERVICE_COMPLETED",
                    "Your " + b.getServiceNameSnapshot() + " is complete. Balance due: "
                            + money(b.balanceDue(), b) + ". Please leave a review!");
        } else {
            notifications.notifyCustomer(b, "SERVICE_COMPLETED",
                    "Thanks! Your " + b.getServiceNameSnapshot() + " is complete. We'd love your review.");
        }
        return response(b);
    }

    @Transactional
    public BookingResponse noShow(UUID vendorId, UUID bookingId, String actor) {
        Booking b = owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT, BookingStatus.IN_PROGRESS);
        transition(b, BookingStatus.NO_SHOW, actor, "Customer did not attend");
        return response(b);
    }

    @Transactional
    public BookingResponse close(UUID vendorId, UUID bookingId, String actor) {
        Booking b = vendorId == null ? bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found")) : owned(vendorId, bookingId);
        requireStatus(b, BookingStatus.COMPLETED, BookingStatus.BALANCE_PENDING);
        transition(b, BookingStatus.CLOSED, actor, "Closed");
        return response(b);
    }

    // ===================== Payments =====================

    /** Vendor-initiated manual payment. Non-card methods require admin-enabled alternative payments. */
    @Transactional
    public PaymentResponse recordPayment(UUID vendorId, UUID bookingId, PaymentRequest req, String actor) {
        Booking b = owned(vendorId, bookingId);
        PaymentMethod method = req.method() == null ? PaymentMethod.OTHER : req.method();
        if (method != PaymentMethod.CARD
                && !vendors.findById(vendorId).map(Vendor::isAlternativePaymentsEnabled).orElse(false)) {
            throw ApiException.forbidden("Your account isn't enabled for non-card payments");
        }
        BigDecimal amount = req.amount() != null ? req.amount() : defaultPaymentAmount(b, req.paymentType());
        return mapper.payment(applyPayment(b, req.paymentType(), amount, method, req.transactionReference(), actor));
    }

    /**
     * Records a payment against a booking and advances its lifecycle. Shared by the vendor's
     * manual recording and by inbound Stripe events ({@link #recordStripePayment}). Refunds
     * decrement the paid total; everything else increments it.
     */
    private BookingPayment applyPayment(Booking b, BookingPaymentType type, BigDecimal amount,
                                        PaymentMethod method, String reference, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("Payment amount must be positive");
        }
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(b.getId());
        payment.setVendorId(b.getVendorId());
        payment.setCustomerUserId(b.getCustomerUserId());
        payment.setPaymentType(type);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setTransactionReference(reference);
        payment.setPaidAt(Instant.now());

        if (type == BookingPaymentType.REFUND) {
            payment.setStatus(PaymentStatus.REFUNDED);
            b.setRefundedAmount(b.getRefundedAmount().add(amount));
            b.setAmountPaid(b.getAmountPaid().subtract(amount).max(BigDecimal.ZERO));
        } else {
            payment.setStatus(PaymentStatus.PAID);
            b.setAmountPaid(b.getAmountPaid().add(amount));
        }
        payments.save(payment);
        applyPaymentStatus(b, actor);
        bookings.save(b);
        log.info("Booking payment {}: {} {} on {} (by {}) -> paid={} status={}",
                type, amount, b.getCurrency(), b.getPublicBookingId(), actor, b.getAmountPaid(), b.getStatus());
        audit.logChange(b.getId(), "BOOKING_PAYMENT", null, type.name(), actor, reference);
        return payment;
    }

    /**
     * Starts (or resumes) a Stripe card payment for a booking's deposit or balance, returning the
     * client secret the customer confirms in the browser. Public/customer-facing: authenticated by
     * the signed-in customer or by a contact match (guest bookings).
     */
    @Transactional(readOnly = true)
    public PaymentInitResponse initiatePayment(String publicBookingId, UUID customerUserId, String contact) {
        Booking b = bookings.findByPublicBookingId(publicBookingId.trim())
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!ownsBooking(b, customerUserId, contact)) {
            throw ApiException.forbidden("You can only pay for your own booking");
        }
        if (!paymentProps.isEnabled()) {
            throw ApiException.badRequest("Online card payment is not available right now");
        }
        boolean depositPhase = b.getStatus() == BookingStatus.DEPOSIT_PENDING;
        BigDecimal amount = depositPhase
                ? b.getDepositAmount().subtract(b.getAmountPaid()).max(BigDecimal.ZERO)
                : b.balanceDue();
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("There's nothing to pay on this booking right now");
        }
        Vendor vendor = vendors.findById(b.getVendorId()).orElseThrow(() -> ApiException.notFound("Provider not found"));
        CreatePaymentResponse r = initiateStripe(b, vendor, amount, depositPhase ? "deposit" : "balance");
        if (r == null || r.clientSecret() == null) {
            throw ApiException.badRequest("Could not start the card payment — please try again");
        }
        return new PaymentInitResponse(b.getPublicBookingId(), r.clientSecret(), r.reference(), amount, b.getCurrency());
    }

    /** Applies a successful Stripe charge (from the payment-service webhook) to a booking. */
    @Transactional
    public void recordStripePayment(String publicBookingId, BigDecimal amount, String reference) {
        bookings.findByPublicBookingId(publicBookingId).ifPresentOrElse(b -> {
            BookingPaymentType type = b.getStatus() == BookingStatus.DEPOSIT_PENDING
                    ? BookingPaymentType.DEPOSIT : BookingPaymentType.BALANCE;
            applyPayment(b, type, amount, PaymentMethod.STRIPE, reference, "stripe");
        }, () -> log.warn("No booking found for {} from Stripe payment event", publicBookingId));
    }

    /** Applies a Stripe refund (from the payment-service webhook) to a booking. */
    @Transactional
    public void recordStripeRefund(String publicBookingId, BigDecimal amount, String reference) {
        bookings.findByPublicBookingId(publicBookingId).ifPresent(b ->
                applyPayment(b, BookingPaymentType.REFUND, amount, PaymentMethod.STRIPE, reference, "stripe"));
    }

    private CreatePaymentResponse initiateStripe(Booking b, Vendor vendor, BigDecimal amount, String suffix) {
        if (!paymentProps.isEnabled() || amount == null || amount.signum() <= 0) {
            return null;
        }
        String customerId = b.getCustomerUserId() != null
                ? b.getCustomerUserId().toString() : "guest:" + b.getPublicBookingId();
        try {
            return paymentClient.createBookingPayment(b.getPublicBookingId(), customerId, b.getVendorId(),
                    b.getCurrency(), amount, vendor.getCommissionRate(), suffix);
        } catch (Exception e) {
            log.warn("payment-service createBookingPayment failed for {} ({}); booking unaffected",
                    b.getPublicBookingId(), e.getMessage());
            return null;
        }
    }

    private void applyPaymentStatus(Booking b, String actor) {
        PaymentStatus before = b.getPaymentStatus();
        BigDecimal paid = b.getAmountPaid();
        if (b.getRefundedAmount().signum() > 0 && paid.signum() <= 0) {
            b.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (paid.compareTo(b.getTotalAmount()) >= 0) {
            b.setPaymentStatus(PaymentStatus.PAID);
        } else if (paid.signum() > 0) {
            b.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            b.setPaymentStatus(PaymentStatus.PENDING);
        }

        // Deposit satisfied → confirm and lock the slot.
        if (b.getStatus() == BookingStatus.DEPOSIT_PENDING
                && paid.compareTo(b.getDepositAmount()) >= 0) {
            b.setHoldExpiresAt(null);
            transition(b, BookingStatus.CONFIRMED, actor, "Deposit paid");
            notifications.notifyCustomer(b, "DEPOSIT_PAID",
                    "Deposit received — your booking " + b.getPublicBookingId() + " is confirmed.");
            notifications.notifyProvider(b, "DEPOSIT_PAID",
                    "Deposit paid for " + b.getPublicBookingId() + "; slot locked.");
        }
        // Fully paid after service → close and request review.
        if ((b.getStatus() == BookingStatus.BALANCE_PENDING || b.getStatus() == BookingStatus.COMPLETED)
                && b.getPaymentStatus() == PaymentStatus.PAID) {
            transition(b, BookingStatus.CLOSED, actor, "Balance settled");
            notifications.notifyCustomer(b, "BOOKING_CLOSED",
                    "Payment complete for " + b.getPublicBookingId() + ". Thank you — please leave a review!");
        }
        if (before != b.getPaymentStatus()) {
            audit.logChange(b.getId(), "BOOKING_PAYMENT_STATUS", before.name(), b.getPaymentStatus().name(),
                    actor, null);
        }
    }

    private BigDecimal defaultPaymentAmount(Booking b, BookingPaymentType type) {
        return switch (type) {
            case DEPOSIT -> b.getDepositAmount().signum() > 0 ? b.getDepositAmount() : b.getTotalAmount();
            case BALANCE -> b.balanceDue();
            case FULL -> b.getTotalAmount();
            case REFUND -> b.getAmountPaid();
        };
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> payments(UUID vendorId, UUID bookingId) {
        owned(vendorId, bookingId);
        return payments.findByBookingIdOrderByCreatedAtAsc(bookingId).stream().map(mapper::payment).toList();
    }

    // ===================== Reminder / hold maintenance (scheduler hooks) =====================

    @Transactional
    public void markReminderSent(UUID bookingId) {
        bookings.findById(bookingId).ifPresent(b -> {
            b.setLastReminderAt(Instant.now());
            if (b.getStatus() == BookingStatus.CONFIRMED) {
                b.setStatus(BookingStatus.REMINDER_SENT);
            }
            bookings.save(b);
        });
    }

    @Transactional
    public int releaseExpiredHolds() {
        List<Booking> expired = bookings.findExpiredHolds(BookingStatus.DEPOSIT_PENDING, Instant.now());
        int released = 0;
        for (Booking b : expired) {
            if (b.getAmountPaid().compareTo(b.getDepositAmount()) >= 0) {
                continue;
            }
            b.setStatusReason("Deposit not paid in time");
            b.setHoldExpiresAt(null);
            transition(b, BookingStatus.CANCELLED, "SYSTEM", "Deposit hold expired");
            notifications.notifyCustomer(b, "DEPOSIT_EXPIRED",
                    "Your booking " + b.getPublicBookingId()
                            + " was released because the deposit wasn't paid in time.");
            released++;
        }
        if (released > 0) {
            log.info("Released {} expired deposit holds", released);
        }
        return released;
    }

    // ===================== Queries =====================

    @Transactional(readOnly = true)
    public List<BookingResponse> vendorBookings(UUID vendorId, Instant from, Instant to) {
        String name = vendorName(vendorId);
        List<Booking> list = (from == null && to == null)
                ? bookings.findByVendorIdOrderByAppointmentStartDesc(vendorId)
                : bookings.findByVendorIdAndAppointmentStartBetweenOrderByAppointmentStartAsc(
                        vendorId, from == null ? Instant.EPOCH : from, to == null ? farFuture() : to);
        return list.stream().map(b -> response(b, name)).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getForVendor(UUID vendorId, UUID bookingId) {
        return response(owned(vendorId, bookingId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> customerBookings(UUID customerUserId) {
        return bookings.findByCustomerUserIdOrderByAppointmentStartDesc(customerUserId).stream()
                .map(b -> response(b, vendorName(b.getVendorId()))).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse track(String publicBookingId, String contact) {
        Booking b = bookings.findByPublicBookingId(publicBookingId.trim())
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        String needle = contact == null ? "" : contact.trim();
        boolean matches = needle.equalsIgnoreCase(b.getCustomerPhone())
                || (b.getCustomerEmail() != null && needle.equalsIgnoreCase(b.getCustomerEmail()));
        if (!matches) {
            throw ApiException.notFound("Booking not found");
        }
        return response(b);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> allBookings() {
        return bookings.findAllByOrderByAppointmentStartDesc().stream()
                .map(b -> response(b, vendorName(b.getVendorId()))).toList();
    }

    // ===================== helpers =====================

    /** Booking entity by id, scoped to the vendor (ownership check). */
    Booking owned(UUID vendorId, UUID bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!b.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This booking belongs to another provider");
        }
        return b;
    }

    private void transition(Booking b, BookingStatus to, String actor, String note) {
        BookingStatus from = b.getStatus();
        b.setStatus(to);
        bookings.save(b);
        audit.logChange(b.getId(), AUDIT_TYPE, from.name(), to.name(), actor, note);
        log.info("Booking {} {} -> {} (by {})", b.getPublicBookingId(), from, to, actor);
    }

    private void requireStatus(Booking b, BookingStatus... allowed) {
        for (BookingStatus s : allowed) {
            if (b.getStatus() == s) {
                return;
            }
        }
        throw ApiException.badRequest("Cannot perform that action while the booking is " + b.getStatus());
    }

    private boolean isTerminal(BookingStatus s) {
        return s == BookingStatus.CLOSED || s == BookingStatus.CANCELLED
                || s == BookingStatus.NO_SHOW || s == BookingStatus.REJECTED;
    }

    private Vendor approvedVendor(UUID vendorId) {
        Vendor vendor = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Provider not found"));
        if (!vendor.isActive() || vendor.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.badRequest("This provider is not currently accepting bookings");
        }
        return vendor;
    }

    private Map<UUID, Integer> selectionMap(List<SelectedAddOn> selected) {
        Map<UUID, Integer> map = new HashMap<>();
        if (selected != null) {
            for (SelectedAddOn s : selected) {
                map.merge(s.addOnId(), s.quantity() == null ? 1 : s.quantity(), Integer::sum);
            }
        }
        return map;
    }

    private static BigDecimal depositFor(DepositType type, BigDecimal value, BigDecimal total) {
        if (type == null) {
            return BigDecimal.ZERO;
        }
        return switch (type) {
            case NONE -> BigDecimal.ZERO;
            case FIXED -> value == null ? BigDecimal.ZERO : value.min(total).setScale(2, RoundingMode.HALF_UP);
            case PERCENTAGE -> value == null ? BigDecimal.ZERO
                    : total.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FULL -> total.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private String money(BigDecimal amount, Booking b) {
        return amount.setScale(2, RoundingMode.HALF_UP) + " " + b.getCurrency();
    }

    private BookingResponse response(Booking b) {
        return response(b, vendorName(b.getVendorId()));
    }

    private BookingResponse response(Booking b, String vendorName) {
        BookingReview review = reviews.findByBookingId(b.getId()).orElse(null);
        return mapper.booking(b, vendorName, review);
    }

    private BookingResponse response(Booking b, String vendorName, CreatePaymentResponse payment) {
        if (payment == null) {
            return response(b, vendorName);
        }
        BookingReview review = reviews.findByBookingId(b.getId()).orElse(null);
        return mapper.booking(b, vendorName, review, payment.clientSecret(), payment.reference());
    }

    /** True when the booking belongs to the signed-in customer, or the contact matches it. */
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

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Provider");
    }

    private String generateBookingId() {
        String id = CodeGenerator.bookingId();
        while (bookings.existsByPublicBookingId(id)) {
            id = CodeGenerator.bookingId();
        }
        return id;
    }

    private static Instant farFuture() {
        return Instant.now().plus(Duration.ofDays(3650));
    }
}
