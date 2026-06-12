package com.myorderlynk.app.booking;
import com.myorderlynk.app.notification.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records booking notifications (PRD §11). MVP delivery is persisted to {@link BookingNotification}
 * and logged; real email/WhatsApp dispatch plugs into the same seam later, mirroring how the
 * order-side {@code NotificationService} records to {@code notification_logs}.
 */
@Service
public class BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);

    public static final String ROLE_PROVIDER = "PROVIDER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    private final BookingNotificationRepository repo;

    public BookingNotificationService(BookingNotificationRepository repo) {
        this.repo = repo;
    }

    /** Notify the customer of a booking event via email when on file, otherwise WhatsApp. */
    public BookingNotification notifyCustomer(Booking booking, String template, String body) {
        boolean hasEmail = booking.getCustomerEmail() != null && !booking.getCustomerEmail().isBlank();
        String channel = hasEmail ? "EMAIL" : "WHATSAPP";
        String recipient = hasEmail ? booking.getCustomerEmail() : booking.getCustomerPhone();
        return record(booking.getId(), ROLE_CUSTOMER, channel, recipient, template, body, null);
    }

    /** Notify the provider (dashboard event) of a booking change. */
    public BookingNotification notifyProvider(Booking booking, String template, String body) {
        return record(booking.getId(), ROLE_PROVIDER, "DASHBOARD", null, template, body, null);
    }

    public BookingNotification record(UUID bookingId, String role, String channel, String recipient,
                                      String template, String body, Instant scheduledAt) {
        BookingNotification n = new BookingNotification();
        n.setBookingId(bookingId);
        n.setRecipientRole(role);
        n.setChannel(channel);
        n.setRecipient(recipient);
        n.setTemplate(template);
        n.setBody(body);
        n.setScheduledAt(scheduledAt);
        n.setSentAt(Instant.now());
        n.setStatus("SENT");
        BookingNotification saved = repo.save(n);
        log.info("[booking-notify:{}] booking={} template={} -> {}", channel, bookingId, template, recipient);
        return saved;
    }

    public boolean alreadySent(UUID bookingId, String template) {
        return repo.existsByBookingIdAndTemplate(bookingId, template);
    }

    public List<BookingNotification> forBooking(UUID bookingId) {
        return repo.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }
}
