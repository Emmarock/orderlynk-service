package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.ServiceDtos.DayAvailabilityResponse;
import com.myorderlynk.app.booking.ServiceDtos.SlotResponse;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes bookable slots from a provider's weekly {@link AvailabilityRule}s, the service
 * duration, buffer, lead time, max-advance window, {@link BlockedSlot}s and existing-booking
 * capacity (PRD §9). Simple and vendor-level for MVP, but extensible to staff/resources later.
 */
@Slf4j
@Service
public class AvailabilityService {

    /** Used when a provider's stored timezone is missing or not a recognized IANA id. */
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Toronto");

    private final ServiceOfferingRepository services;
    private final ServiceProviderProfileRepository profiles;
    private final AvailabilityRuleRepository rules;
    private final BlockedSlotRepository blocked;
    private final BookingRepository bookings;

    public AvailabilityService(ServiceOfferingRepository services, ServiceProviderProfileRepository profiles,
                               AvailabilityRuleRepository rules, BlockedSlotRepository blocked,
                               BookingRepository bookings) {
        this.services = services;
        this.profiles = profiles;
        this.rules = rules;
        this.blocked = blocked;
        this.bookings = bookings;
    }

    /** Bookable slots for a service on a calendar date (in the provider's timezone). */
    @Transactional(readOnly = true)
    public DayAvailabilityResponse availableSlots(UUID serviceId, LocalDate date) {
        ServiceOffering service = services.findById(serviceId)
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!service.isActive()) {
            throw ApiException.badRequest("This service is not currently bookable");
        }
        ServiceProviderProfile profile = profile(service.getVendorId());
        ZoneId zone = zoneOf(profile);
        int duration = service.getDurationMinutes();

        List<SlotResponse> slots = new ArrayList<>();
        List<AvailabilityRule> dayRules = rules.findByVendorIdAndActiveTrue(service.getVendorId()).stream()
                .filter(r -> r.getDayOfWeek() == date.getDayOfWeek())
                .toList();
        if (dayRules.isEmpty()) {
            return new DayAvailabilityResponse(serviceId, date.toString(), duration, slots);
        }

        Instant now = Instant.now();
        Instant earliest = now.plus(Duration.ofHours(effectiveLead(profile, dayRules.get(0))));
        Instant latest = LocalDate.now(zone).plusDays(profile.getMaxAdvanceDays())
                .atTime(LocalTime.MAX).atZone(zone).toInstant();
        List<BlockedSlot> blocks = blocked.findByVendorId(service.getVendorId());

        for (AvailabilityRule rule : dayRules) {
            int buffer = rule.getBufferMinutes() != null ? rule.getBufferMinutes() : profile.getBufferMinutes();
            int capacity = rule.getCapacity() != null ? rule.getCapacity() : profile.getDefaultCapacity();
            int step = Math.max(duration + buffer, 5);

            LocalDateTime cursor = LocalDateTime.of(date, rule.getStartTime());
            LocalDateTime windowEnd = LocalDateTime.of(date, rule.getEndTime());
            while (!cursor.plusMinutes(duration).isAfter(windowEnd)) {
                Instant start = cursor.atZone(zone).toInstant();
                Instant end = cursor.plusMinutes(duration).atZone(zone).toInstant();
                cursor = cursor.plusMinutes(step);

                if (start.isBefore(earliest) || start.isAfter(latest)) {
                    continue;
                }
                if (overlapsBlock(blocks, start, end)) {
                    continue;
                }
                int remaining = capacity - countOverlapping(service.getVendorId(), start, end);
                if (remaining > 0) {
                    slots.add(new SlotResponse(start, end, remaining));
                }
            }
        }
        slots.sort((a, b) -> a.start().compareTo(b.start()));
        return new DayAvailabilityResponse(serviceId, date.toString(), duration, slots);
    }

    /**
     * Validates that an explicit appointment window is bookable for a service, reserving against
     * lead time, max-advance, working hours, blocks and capacity. Throws {@link ApiException} otherwise.
     */
    @Transactional(readOnly = true)
    public void assertBookable(ServiceProviderProfile profile, UUID vendorId, Instant start, Instant end) {
        ZoneId zone = zoneOf(profile);
        LocalDateTime localStart = LocalDateTime.ofInstant(start, zone);
        LocalDateTime localEnd = LocalDateTime.ofInstant(end, zone);

        List<AvailabilityRule> dayRules = rules.findByVendorIdAndActiveTrue(vendorId).stream()
                .filter(r -> r.getDayOfWeek() == localStart.getDayOfWeek())
                .toList();
        AvailabilityRule covering = dayRules.stream()
                .filter(r -> !localStart.toLocalTime().isBefore(r.getStartTime())
                        && !localEnd.toLocalTime().isAfter(r.getEndTime())
                        && localStart.toLocalDate().equals(localEnd.toLocalDate()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("That time is outside the provider's working hours"));

        Instant now = Instant.now();
        if (start.isBefore(now.plus(Duration.ofHours(effectiveLead(profile, covering))))) {
            throw ApiException.badRequest("That time is too soon — please pick a later slot");
        }
        Instant latest = LocalDate.now(zone).plusDays(profile.getMaxAdvanceDays())
                .atTime(LocalTime.MAX).atZone(zone).toInstant();
        if (start.isAfter(latest)) {
            throw ApiException.badRequest("That time is beyond how far ahead this provider accepts bookings");
        }
        if (overlapsBlock(blocked.findByVendorId(vendorId), start, end)) {
            throw ApiException.badRequest("The provider is unavailable during that period");
        }
        int capacity = covering.getCapacity() != null ? covering.getCapacity() : profile.getDefaultCapacity();
        if (countOverlapping(vendorId, start, end) >= capacity) {
            throw ApiException.badRequest("That slot is already fully booked");
        }
    }

    private ServiceProviderProfile profile(UUID vendorId) {
        return profiles.findByVendorId(vendorId)
                .orElseThrow(() -> ApiException.badRequest("This provider has not set up availability yet"));
    }

    /**
     * Resolves the provider's timezone, tolerating missing or legacy/free-text values. Slot
     * generation must never crash just because a profile holds e.g. "EST" or "" — that would
     * surface to the customer as "no slots". Falls back to {@link #DEFAULT_ZONE} and logs.
     */
    private ZoneId zoneOf(ServiceProviderProfile profile) {
        String tz = profile.getTimezone();
        if (tz != null && !tz.isBlank()) {
            try {
                return ZoneId.of(tz.trim());
            } catch (DateTimeException e) {
                log.warn("Vendor {} has an unrecognized service timezone '{}' — using {} for slots",
                        profile.getVendorId(), tz, DEFAULT_ZONE);
            }
        }
        return DEFAULT_ZONE;
    }

    private int effectiveLead(ServiceProviderProfile profile, AvailabilityRule rule) {
        return rule.getLeadTimeHours() != null ? rule.getLeadTimeHours() : profile.getLeadTimeHours();
    }

    private boolean overlapsBlock(List<BlockedSlot> blocks, Instant start, Instant end) {
        return blocks.stream().anyMatch(b ->
                b.getStartDatetime().isBefore(end) && b.getEndDatetime().isAfter(start));
    }

    private int countOverlapping(UUID vendorId, Instant start, Instant end) {
        return bookings.findOverlapping(vendorId, start, end, BookingRepository.OCCUPYING_STATUSES).size();
    }
}
