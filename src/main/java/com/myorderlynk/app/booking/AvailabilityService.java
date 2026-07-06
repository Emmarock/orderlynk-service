package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.ServiceDtos.DayAvailabilityResponse;
import com.myorderlynk.app.booking.ServiceDtos.SlotResponse;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes bookable slots from a provider's weekly {@link AvailabilityRule}s, the service
 * duration, buffer, lead time, max-advance window, {@link BlockedSlot}s and existing-booking
 * capacity (PRD §9).
 *
 * <p>Availability resolves at two levels. A provider with no bookable team members books at the
 * <em>shop</em> level (rules/blocks with {@code staffId == null}, capacity from the rule/profile).
 * Once a provider adds bookable workers, a customer either picks one — then only that worker's
 * calendar is offered (own hours or, if they set none, the shop's; shop ∪ personal blocks;
 * capacity 1) — or asks for "any available", in which case each worker's free slots are unioned
 * and the remaining capacity of a slot is the number of free workers.
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
    private final StaffMemberRepository staff;

    public AvailabilityService(ServiceOfferingRepository services, ServiceProviderProfileRepository profiles,
                               AvailabilityRuleRepository rules, BlockedSlotRepository blocked,
                               BookingRepository bookings, StaffMemberRepository staff) {
        this.services = services;
        this.profiles = profiles;
        this.rules = rules;
        this.blocked = blocked;
        this.bookings = bookings;
        this.staff = staff;
    }

    /** Bookable slots for a service on a calendar date, at the shop level. */
    @Transactional(readOnly = true)
    public DayAvailabilityResponse availableSlots(UUID serviceId, LocalDate date) {
        return availableSlots(serviceId, date, null);
    }

    /**
     * Bookable slots for a service on a calendar date. A non-null {@code staffId} restricts to that
     * worker; a null {@code staffId} unions the team when the provider has bookable staff, otherwise
     * falls back to shop-level availability.
     */
    @Transactional(readOnly = true)
    public DayAvailabilityResponse availableSlots(UUID serviceId, LocalDate date, UUID staffId) {
        ServiceOffering service = services.findById(serviceId)
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!service.isActive()) {
            throw ApiException.badRequest("This service is not currently bookable");
        }
        ServiceProviderProfile profile = profile(service.getVendorId());
        ZoneId zone = zoneOf(profile);
        int duration = service.getDurationMinutes();
        UUID vendorId = service.getVendorId();

        List<SlotResponse> slots;
        if (staffId != null) {
            // A specific worker was chosen — offer only their calendar (empty if they don't offer it).
            StaffMember worker = staff.findById(staffId).orElse(null);
            slots = worker != null && worker.getVendorId().equals(vendorId)
                    && worker.isBookable() && worker.offersService(serviceId)
                    ? staffSlots(profile, zone, date, duration, vendorId, worker.getId())
                    : new ArrayList<>();
        } else {
            List<StaffMember> team = bookableStaffFor(vendorId, serviceId);
            slots = team.isEmpty()
                    ? shopSlots(profile, zone, date, duration, vendorId)  // back-compat: no team
                    : aggregateStaffSlots(profile, zone, date, duration, vendorId, team);
        }
        slots.sort((a, b) -> a.start().compareTo(b.start()));
        return new DayAvailabilityResponse(serviceId, date.toString(), duration, slots);
    }

    /**
     * Validates that an explicit appointment window is bookable for a service (shop level). Throws
     * {@link ApiException} otherwise.
     */
    @Transactional(readOnly = true)
    public void assertBookable(ServiceProviderProfile profile, UUID vendorId, Instant start, Instant end) {
        assertBookable(profile, vendorId, null, start, end);
    }

    /**
     * Validates that an explicit appointment window is bookable, reserving against lead time,
     * max-advance, working hours, blocks and capacity. When {@code staffId} is set the checks are
     * scoped to that worker (their hours, shop ∪ personal blocks, capacity 1); otherwise they are
     * shop-wide. Throws {@link ApiException} otherwise.
     */
    @Transactional(readOnly = true)
    public void assertBookable(ServiceProviderProfile profile, UUID vendorId, UUID staffId,
                               Instant start, Instant end) {
        ZoneId zone = zoneOf(profile);
        LocalDateTime localStart = LocalDateTime.ofInstant(start, zone);
        LocalDateTime localEnd = LocalDateTime.ofInstant(end, zone);

        List<AvailabilityRule> dayRules = dayRules(vendorId, staffId, localStart.getDayOfWeek());
        AvailabilityRule covering = dayRules.stream()
                .filter(r -> !localStart.toLocalTime().isBefore(r.getStartTime())
                        && !localEnd.toLocalTime().isAfter(r.getEndTime())
                        && localStart.toLocalDate().equals(localEnd.toLocalDate()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(staffId == null
                        ? "That time is outside the provider's working hours"
                        : "That time is outside this team member's working hours"));

        Instant now = Instant.now();
        if (start.isBefore(now.plus(Duration.ofHours(effectiveLead(profile, covering))))) {
            throw ApiException.badRequest("That time is too soon — please pick a later slot");
        }
        Instant latest = LocalDate.now(zone).plusDays(profile.getMaxAdvanceDays())
                .atTime(LocalTime.MAX).atZone(zone).toInstant();
        if (start.isAfter(latest)) {
            throw ApiException.badRequest("That time is beyond how far ahead this provider accepts bookings");
        }
        if (overlapsBlock(blocksFor(vendorId, staffId), start, end)) {
            throw ApiException.badRequest(staffId == null
                    ? "The provider is unavailable during that period"
                    : "That team member is unavailable during that period");
        }
        if (staffId == null) {
            int capacity = covering.getCapacity() != null ? covering.getCapacity() : profile.getDefaultCapacity();
            if (countOverlapping(vendorId, start, end) >= capacity) {
                throw ApiException.badRequest("That slot is already fully booked");
            }
        } else if (!bookings.findOverlappingByStaff(vendorId, staffId, start, end,
                BookingRepository.OCCUPYING_STATUSES).isEmpty()) {
            throw ApiException.badRequest("That team member is already booked at that time");
        }
    }

    /**
     * The first candidate worker who is free for the whole window, for "any available" auto-assign.
     * {@code candidates} should already be filtered to workers who offer the service.
     */
    @Transactional(readOnly = true)
    public Optional<StaffMember> firstAvailableStaff(ServiceProviderProfile profile, UUID vendorId,
                                                     List<StaffMember> candidates, Instant start, Instant end) {
        return candidates.stream()
                .filter(s -> isStaffBookable(profile, vendorId, s.getId(), start, end))
                .findFirst();
    }

    /** A vendor's bookable team members that offer the given service (empty offerings = all). */
    @Transactional(readOnly = true)
    public List<StaffMember> bookableStaffFor(UUID vendorId, UUID serviceId) {
        return staff.findByVendorIdAndActiveTrueAndAcceptsBookingsTrueOrderByDisplayOrderAscCreatedAtAsc(vendorId)
                .stream()
                .filter(s -> s.offersService(serviceId))
                .toList();
    }

    // ===================== slot generation =====================

    /** Shop-level slots — capacity from rule/profile, counted against all of the vendor's bookings. */
    private List<SlotResponse> shopSlots(ServiceProviderProfile profile, ZoneId zone, LocalDate date,
                                         int duration, UUID vendorId) {
        List<AvailabilityRule> dayRules = dayRules(vendorId, null, date.getDayOfWeek());
        List<BlockedSlot> blocks = blocksFor(vendorId, null);
        return generateSlots(profile, zone, date, duration, vendorId, null, dayRules, blocks);
    }

    /** One worker's slots — capacity 1, counted against that worker's bookings only. */
    private List<SlotResponse> staffSlots(ServiceProviderProfile profile, ZoneId zone, LocalDate date,
                                          int duration, UUID vendorId, UUID staffId) {
        List<AvailabilityRule> dayRules = dayRules(vendorId, staffId, date.getDayOfWeek());
        List<BlockedSlot> blocks = blocksFor(vendorId, staffId);
        return generateSlots(profile, zone, date, duration, vendorId, staffId, dayRules, blocks);
    }

    /** Union of every candidate worker's slots; remaining capacity = number of free workers. */
    private List<SlotResponse> aggregateStaffSlots(ServiceProviderProfile profile, ZoneId zone, LocalDate date,
                                                   int duration, UUID vendorId, List<StaffMember> team) {
        Map<Instant, SlotResponse> byStart = new LinkedHashMap<>();
        for (StaffMember worker : team) {
            for (SlotResponse slot : staffSlots(profile, zone, date, duration, vendorId, worker.getId())) {
                byStart.merge(slot.start(), slot, (existing, added) ->
                        new SlotResponse(existing.start(), existing.end(),
                                existing.remainingCapacity() + added.remainingCapacity()));
            }
        }
        return new ArrayList<>(byStart.values());
    }

    /**
     * Core slot loop shared by shop-level and per-worker computation. When {@code staffId} is null
     * capacity comes from the rule/profile and counts all vendor bookings; otherwise capacity is 1
     * and counts only that worker's bookings.
     */
    private List<SlotResponse> generateSlots(ServiceProviderProfile profile, ZoneId zone, LocalDate date,
                                             int duration, UUID vendorId, UUID staffId,
                                             List<AvailabilityRule> dayRules, List<BlockedSlot> blocks) {
        List<SlotResponse> slots = new ArrayList<>();
        if (dayRules.isEmpty()) {
            return slots;
        }
        Instant now = Instant.now();
        Instant earliest = now.plus(Duration.ofHours(effectiveLead(profile, dayRules.get(0))));
        Instant latest = LocalDate.now(zone).plusDays(profile.getMaxAdvanceDays())
                .atTime(LocalTime.MAX).atZone(zone).toInstant();

        for (AvailabilityRule rule : dayRules) {
            int buffer = rule.getBufferMinutes() != null ? rule.getBufferMinutes() : profile.getBufferMinutes();
            int capacity = staffId != null ? 1
                    : (rule.getCapacity() != null ? rule.getCapacity() : profile.getDefaultCapacity());
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
                int occupied = staffId != null
                        ? bookings.findOverlappingByStaff(vendorId, staffId, start, end, BookingRepository.OCCUPYING_STATUSES).size()
                        : countOverlapping(vendorId, start, end);
                int remaining = capacity - occupied;
                if (remaining > 0) {
                    slots.add(new SlotResponse(start, end, remaining));
                }
            }
        }
        return slots;
    }

    // ===================== rule / block resolution =====================

    /**
     * The active rules for a day. For a worker: their own rules if they have defined any (across the
     * week), otherwise the shop-wide rules they inherit. For the shop: shop-wide rules only.
     */
    private List<AvailabilityRule> dayRules(UUID vendorId, UUID staffId, DayOfWeek day) {
        List<AvailabilityRule> effective;
        if (staffId == null) {
            effective = rules.findByVendorIdAndActiveTrueAndStaffIdIsNull(vendorId);
        } else {
            List<AvailabilityRule> own = rules.findByVendorIdAndActiveTrueAndStaffId(vendorId, staffId);
            effective = own.isEmpty() ? rules.findByVendorIdAndActiveTrueAndStaffIdIsNull(vendorId) : own;
        }
        return effective.stream().filter(r -> r.getDayOfWeek() == day).toList();
    }

    /** Blocks that apply: shop-wide only for the shop; shop-wide ∪ personal for a worker. */
    private List<BlockedSlot> blocksFor(UUID vendorId, UUID staffId) {
        List<BlockedSlot> blocks = new ArrayList<>(blocked.findByVendorIdAndStaffIdIsNull(vendorId));
        if (staffId != null) {
            blocks.addAll(blocked.findByVendorIdAndStaffId(vendorId, staffId));
        }
        return blocks;
    }

    /** Boolean form of {@link #assertBookable} for auto-assign scanning. */
    private boolean isStaffBookable(ServiceProviderProfile profile, UUID vendorId, UUID staffId,
                                    Instant start, Instant end) {
        try {
            assertBookable(profile, vendorId, staffId, start, end);
            return true;
        } catch (ApiException e) {
            return false;
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
