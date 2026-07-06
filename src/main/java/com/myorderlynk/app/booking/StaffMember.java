package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A team member / worker at a service provider — e.g. an individual barber or hair braider in a
 * shop that has several. Customers can browse the team and book a specific worker, each of whom
 * has their own calendar (personal {@link AvailabilityRule}s and {@link BlockedSlot}s keyed by
 * {@code staffId}). Staff are owner-managed profiles; they do not log in.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_staff", indexes = @Index(name = "idx_staff_vendor", columnList = "vendorId"))
public class StaffMember extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    /** Role shown to customers, e.g. "Senior Barber", "Master Braider". */
    private String title;

    @Column(length = 2000)
    private String bio;

    @Column(length = 1024)
    private String photoUrl;

    @Column(nullable = false)
    private boolean active = true;

    /** When false the worker is shown on the team but cannot be booked (e.g. on extended leave). */
    @Column(nullable = false)
    private boolean acceptsBookings = true;

    /** Manual ordering of the team on the storefront. */
    @Column(nullable = false)
    private int displayOrder = 0;

    /**
     * Ids of the services this worker offers. Empty means the worker offers <em>all</em> of the
     * shop's services (the common case). Non-empty restricts them to specialists' services.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_staff_offerings", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "service_id")
    private Set<UUID> serviceIds = new HashSet<>();

    /** True when this worker can be booked for the given service (empty offerings = all services). */
    public boolean offersService(UUID serviceId) {
        return serviceIds.isEmpty() || serviceIds.contains(serviceId);
    }

    /** True when this worker is visible and bookable by customers. */
    public boolean isBookable() {
        return active && acceptsBookings;
    }
}
