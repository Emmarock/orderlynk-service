package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-vendor service-provider settings (PRD §13 ServiceProviderProfile). Created lazily the
 * first time a vendor enables the Services module. Holds the booking policies and the
 * vendor-level scheduling defaults that individual {@link AvailabilityRule}s can override.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_provider_profiles",
        indexes = @Index(name = "uq_spp_vendor", columnList = "vendorId", unique = true))
public class ServiceProviderProfile extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID vendorId;

    /** Master switch for the Services module on this account (PRD §6/§7 step 1). */
    @Column(nullable = false)
    private boolean serviceEnabled = true;

    /** Short provider bio shown on the service storefront. */
    @Column(length = 2000)
    private String bio;

    /** Free-text service area (e.g. "Toronto + GTA") for mobile providers / discovery. */
    private String serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceLocationType locationType = ServiceLocationType.AT_PROVIDER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalMode approvalMode = ApprovalMode.MANUAL;

    @Column(length = 2000)
    private String cancellationPolicy;

    @Column(length = 2000)
    private String depositPolicy;

    /** Human-readable summary of working hours, shown on the storefront. */
    private String businessHoursSummary;

    // ---- Scheduling defaults (overridable per AvailabilityRule) ----

    /** Minimum hours between "now" and the start of a bookable slot (PRD §9 lead time). */
    @Column(nullable = false)
    private int leadTimeHours = 12;

    /** Gap reserved after each booking before the next bookable slot (PRD §9 buffer). */
    @Column(nullable = false)
    private int bufferMinutes = 0;

    /** How far ahead a customer may book (PRD §9 max advance booking). */
    @Column(nullable = false)
    private int maxAdvanceDays = 30;

    /** Default simultaneous-booking capacity for a slot (PRD §9 capacity). */
    @Column(nullable = false)
    private int defaultCapacity = 1;

    /** Minutes a slot is held while the customer completes booking/deposit (PRD §14). */
    @Column(nullable = false)
    private int slotHoldMinutes = 15;

    /** IANA timezone the working hours and slots are expressed in. */
    @Column(nullable = false)
    private String timezone = "America/Toronto";
}
