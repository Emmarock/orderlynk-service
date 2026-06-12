package com.myorderlynk.app.booking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drives the reminder engine (PRD §11) and slot-hold maintenance (PRD §14). Runs every few
 * minutes: sends 24h and 2h pre-appointment reminders (once each) and releases expired,
 * unpaid deposit holds. Reminder dedupe is by {@link BookingNotification} template existence.
 */
@Component
@Slf4j
public class BookingReminderScheduler {

    private static final String T24H = "REMINDER_24H";
    private static final String T2H = "REMINDER_2H";

    private final BookingRepository bookings;
    private final BookingNotificationService notifications;
    private final BookingService bookingService;

    public BookingReminderScheduler(BookingRepository bookings, BookingNotificationService notifications,
                                    BookingService bookingService) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.bookingService = bookingService;
    }

    /** Every 5 minutes: release expired holds, then send due reminders. */
    @Scheduled(cron = "${app.bookings.reminder-cron:0 */5 * * * *}")
    @Transactional
    public void tick() {
        bookingService.releaseExpiredHolds();
        sendWindow(T24H, 24);
        sendWindow(T2H, 2);
    }

    /**
     * Sends the reminder {@code template} for bookings whose appointment is within {@code hours}
     * from now (and still in the future), skipping any already sent.
     */
    private void sendWindow(String template, int hours) {
        Instant now = Instant.now();
        Instant horizon = now.plus(Duration.ofHours(hours));
        List<BookingStatus> active = List.of(BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT);
        List<Booking> upcoming = bookings.findUpcoming(now, horizon, active);
        for (Booking b : upcoming) {
            if (notifications.alreadySent(b.getId(), template)) {
                continue;
            }
            notifications.record(b.getId(), BookingNotificationService.ROLE_CUSTOMER,
                    b.getCustomerEmail() != null && !b.getCustomerEmail().isBlank() ? "EMAIL" : "WHATSAPP",
                    b.getCustomerEmail() != null && !b.getCustomerEmail().isBlank()
                            ? b.getCustomerEmail() : b.getCustomerPhone(),
                    template,
                    "Reminder: your " + b.getServiceNameSnapshot() + " booking " + b.getPublicBookingId()
                            + " is coming up.", null);
            bookingService.markReminderSent(b.getId());
        }
        if (!upcoming.isEmpty()) {
            log.debug("Reminder pass {}: {} upcoming bookings considered", template, upcoming.size());
        }
    }
}
