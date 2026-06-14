package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.ServiceDtos.AddOnRequest;
import com.myorderlynk.app.booking.ServiceDtos.AddOnResponse;
import com.myorderlynk.app.booking.ServiceDtos.AvailabilityRuleRequest;
import com.myorderlynk.app.booking.ServiceDtos.AvailabilityRuleResponse;
import com.myorderlynk.app.booking.ServiceDtos.BlockedSlotRequest;
import com.myorderlynk.app.booking.ServiceDtos.BlockedSlotResponse;
import com.myorderlynk.app.booking.ServiceDtos.ProfileRequest;
import com.myorderlynk.app.booking.ServiceDtos.ProfileResponse;
import com.myorderlynk.app.booking.ServiceDtos.ServiceRequest;
import com.myorderlynk.app.booking.ServiceDtos.ServiceResponse;
import com.myorderlynk.app.common.PageRequests;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.integration.ImageUploads;
import com.myorderlynk.app.integration.S3StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vendor-facing management of the service catalog, provider profile and availability config
 * (PRD §7 steps 1–5, §13). All write methods are scoped by vendor id so a vendor can only
 * touch its own services.
 */
@Service
@Slf4j
public class ServiceCatalogService {

    private final ServiceProviderProfileRepository profiles;
    private final ServiceOfferingRepository services;
    private final ServiceAddOnRepository addOns;
    private final AvailabilityRuleRepository rules;
    private final BlockedSlotRepository blocked;
    private final BookingMapper mapper;
    private final S3StorageService storage;

    public ServiceCatalogService(ServiceProviderProfileRepository profiles, ServiceOfferingRepository services,
                                 ServiceAddOnRepository addOns, AvailabilityRuleRepository rules,
                                 BlockedSlotRepository blocked, BookingMapper mapper, S3StorageService storage) {
        this.profiles = profiles;
        this.services = services;
        this.addOns = addOns;
        this.rules = rules;
        this.blocked = blocked;
        this.mapper = mapper;
        this.storage = storage;
    }

    /**
     * Store a service image uploaded from the vendor's device in S3 and return its public URL.
     * The vendor saves this URL as the service's {@code imageUrl} (mirrors product image upload).
     */
    public String uploadServiceImage(UUID vendorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No image file was provided");
        }
        String contentType = file.getContentType();
        String ext = ImageUploads.extensionOrThrow(contentType);
        String key = "services/" + vendorId + "/" + UUID.randomUUID() + "." + ext;
        log.info("Uploading service image for vendor {}: type={} size={}B key={}",
                vendorId, contentType, file.getSize(), key);
        try {
            String url = storage.uploadPublic(file.getBytes(), contentType, key);
            log.info("Service image uploaded for vendor {} -> {}", vendorId, url);
            return url;
        } catch (IOException e) {
            log.error("Failed to read uploaded service image for vendor {}", vendorId, e);
            throw ApiException.badRequest("Could not read the uploaded image");
        }
    }

    // ---- Provider profile ----

    /** The vendor's profile, creating an enabled default the first time Services is opened. */
    @Transactional
    public ProfileResponse getOrCreateProfile(UUID vendorId) {
        return mapper.profile(profileEntity(vendorId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID vendorId, ProfileRequest req) {
        ServiceProviderProfile p = profileEntity(vendorId);
        if (req.serviceEnabled() != null) p.setServiceEnabled(req.serviceEnabled());
        p.setBio(req.bio());
        p.setServiceArea(req.serviceArea());
        if (req.locationType() != null) p.setLocationType(req.locationType());
        if (req.approvalMode() != null) p.setApprovalMode(req.approvalMode());
        p.setCancellationPolicy(req.cancellationPolicy());
        p.setDepositPolicy(req.depositPolicy());
        p.setBusinessHoursSummary(req.businessHoursSummary());
        if (req.leadTimeHours() != null) p.setLeadTimeHours(req.leadTimeHours());
        if (req.bufferMinutes() != null) p.setBufferMinutes(req.bufferMinutes());
        if (req.maxAdvanceDays() != null) p.setMaxAdvanceDays(req.maxAdvanceDays());
        if (req.defaultCapacity() != null) p.setDefaultCapacity(req.defaultCapacity());
        if (req.slotHoldMinutes() != null) p.setSlotHoldMinutes(req.slotHoldMinutes());
        if (req.timezone() != null && !req.timezone().isBlank()) p.setTimezone(req.timezone());
        log.info("Service profile updated for vendor {} (enabled={}, approval={})",
                vendorId, p.isServiceEnabled(), p.getApprovalMode());
        return mapper.profile(profiles.save(p));
    }

    ServiceProviderProfile profileEntity(UUID vendorId) {
        return profiles.findByVendorId(vendorId).orElseGet(() -> {
            ServiceProviderProfile p = new ServiceProviderProfile();
            p.setVendorId(vendorId);
            p.setServiceEnabled(true);
            ServiceProviderProfile saved = profiles.save(p);
            log.info("Service provider profile created for vendor {}", vendorId);
            return saved;
        });
    }

    // ---- Services ----

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> listServices(UUID vendorId, int page, int size) {
        return PageResponse.of(services.findByVendorIdOrderByCreatedAtDesc(vendorId, PageRequests.of(page, size))
                .map(s -> mapper.service(s, addOns.findByServiceIdOrderByCreatedAtAsc(s.getId()))));
    }

    @Transactional(readOnly = true)
    public ServiceResponse getService(UUID vendorId, UUID serviceId) {
        ServiceOffering s = ownedService(vendorId, serviceId);
        return mapper.service(s, addOns.findByServiceIdOrderByCreatedAtAsc(serviceId));
    }

    @Transactional
    public ServiceResponse createService(UUID vendorId, ServiceRequest req) {
        profileEntity(vendorId); // ensure Services is enabled / profile exists
        ServiceOffering s = new ServiceOffering();
        s.setVendorId(vendorId);
        apply(s, req);
        ServiceOffering saved = services.save(s);
        log.info("Service created: {} '{}' for vendor {}", saved.getId(), saved.getName(), vendorId);
        return mapper.service(saved, List.of());
    }

    @Transactional
    public ServiceResponse updateService(UUID vendorId, UUID serviceId, ServiceRequest req) {
        ServiceOffering s = ownedService(vendorId, serviceId);
        apply(s, req);
        log.info("Service updated: {} for vendor {}", serviceId, vendorId);
        return mapper.service(services.save(s), addOns.findByServiceIdOrderByCreatedAtAsc(serviceId));
    }

    @Transactional
    public ServiceResponse toggleService(UUID vendorId, UUID serviceId, boolean active) {
        ServiceOffering s = ownedService(vendorId, serviceId);
        s.setActive(active);
        return mapper.service(services.save(s), addOns.findByServiceIdOrderByCreatedAtAsc(serviceId));
    }

    @Transactional
    public void deleteService(UUID vendorId, UUID serviceId) {
        ServiceOffering s = ownedService(vendorId, serviceId);
        addOns.deleteByServiceId(serviceId);
        services.delete(s);
        log.info("Service deleted: {} for vendor {}", serviceId, vendorId);
    }

    private void apply(ServiceOffering s, ServiceRequest req) {
        s.setName(req.name());
        if (req.category() != null) s.setCategory(req.category());
        s.setDescription(req.description());
        s.setBasePrice(req.basePrice());
        if (req.currency() != null && !req.currency().isBlank()) s.setCurrency(req.currency());
        s.setDurationMinutes(req.durationMinutes());
        s.setImageUrl(req.imageUrl());
        DepositType depositType = req.depositType() == null ? DepositType.NONE : req.depositType();
        s.setDepositType(depositType);
        s.setDepositValue(depositType == DepositType.NONE ? null : req.depositValue());
        if (depositType == DepositType.PERCENTAGE && req.depositValue() != null
                && req.depositValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("Percentage deposit cannot exceed 100%");
        }
        s.setTaxRate(req.taxRate() == null ? BigDecimal.ZERO : req.taxRate());
        if (req.active() != null) s.setActive(req.active());
    }

    private ServiceOffering ownedService(UUID vendorId, UUID serviceId) {
        ServiceOffering s = services.findById(serviceId)
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!s.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This service belongs to another vendor");
        }
        return s;
    }

    // ---- Add-ons ----

    @Transactional
    public AddOnResponse addAddOn(UUID vendorId, UUID serviceId, AddOnRequest req) {
        ownedService(vendorId, serviceId);
        ServiceAddOn a = new ServiceAddOn();
        a.setServiceId(serviceId);
        a.setVendorId(vendorId);
        applyAddOn(a, req);
        return mapper.addOn(addOns.save(a));
    }

    @Transactional
    public AddOnResponse updateAddOn(UUID vendorId, UUID serviceId, UUID addOnId, AddOnRequest req) {
        ServiceAddOn a = ownedAddOn(vendorId, serviceId, addOnId);
        applyAddOn(a, req);
        return mapper.addOn(addOns.save(a));
    }

    @Transactional
    public void deleteAddOn(UUID vendorId, UUID serviceId, UUID addOnId) {
        ServiceAddOn a = ownedAddOn(vendorId, serviceId, addOnId);
        addOns.delete(a);
    }

    private void applyAddOn(ServiceAddOn a, AddOnRequest req) {
        a.setName(req.name());
        a.setPriceDelta(req.priceDelta());
        a.setDurationDelta(req.durationDelta());
        if (req.required() != null) a.setRequired(req.required());
        if (req.maxSelection() != null) a.setMaxSelection(req.maxSelection());
        if (req.active() != null) a.setActive(req.active());
    }

    private ServiceAddOn ownedAddOn(UUID vendorId, UUID serviceId, UUID addOnId) {
        ServiceAddOn a = addOns.findById(addOnId)
                .orElseThrow(() -> ApiException.notFound("Add-on not found"));
        if (!a.getVendorId().equals(vendorId) || !a.getServiceId().equals(serviceId)) {
            throw ApiException.forbidden("This add-on belongs to another service");
        }
        return a;
    }

    // ---- Availability rules ----

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> listRules(UUID vendorId) {
        return rules.findByVendorId(vendorId).stream().map(mapper::rule).toList();
    }

    @Transactional
    public AvailabilityRuleResponse addRule(UUID vendorId, AvailabilityRuleRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("End time must be after start time");
        }
        AvailabilityRule r = new AvailabilityRule();
        r.setVendorId(vendorId);
        applyRule(r, req);
        return mapper.rule(rules.save(r));
    }

    @Transactional
    public AvailabilityRuleResponse updateRule(UUID vendorId, UUID ruleId, AvailabilityRuleRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("End time must be after start time");
        }
        AvailabilityRule r = ownedRule(vendorId, ruleId);
        applyRule(r, req);
        return mapper.rule(rules.save(r));
    }

    @Transactional
    public void deleteRule(UUID vendorId, UUID ruleId) {
        rules.delete(ownedRule(vendorId, ruleId));
    }

    private void applyRule(AvailabilityRule r, AvailabilityRuleRequest req) {
        r.setDayOfWeek(req.dayOfWeek());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setCapacity(req.capacity());
        r.setBufferMinutes(req.bufferMinutes());
        r.setLeadTimeHours(req.leadTimeHours());
        if (req.active() != null) r.setActive(req.active());
    }

    private AvailabilityRule ownedRule(UUID vendorId, UUID ruleId) {
        AvailabilityRule r = rules.findById(ruleId)
                .orElseThrow(() -> ApiException.notFound("Availability rule not found"));
        if (!r.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This rule belongs to another vendor");
        }
        return r;
    }

    // ---- Blocked slots ----

    @Transactional(readOnly = true)
    public List<BlockedSlotResponse> listBlocked(UUID vendorId) {
        return blocked.findByVendorId(vendorId).stream().map(mapper::blocked).toList();
    }

    @Transactional
    public BlockedSlotResponse addBlocked(UUID vendorId, BlockedSlotRequest req) {
        if (!req.endDatetime().isAfter(req.startDatetime())) {
            throw ApiException.badRequest("Block end must be after start");
        }
        BlockedSlot b = new BlockedSlot();
        b.setVendorId(vendorId);
        b.setStartDatetime(req.startDatetime());
        b.setEndDatetime(req.endDatetime());
        b.setReason(req.reason());
        return mapper.blocked(blocked.save(b));
    }

    @Transactional
    public void deleteBlocked(UUID vendorId, UUID blockedId) {
        BlockedSlot b = blocked.findById(blockedId)
                .orElseThrow(() -> ApiException.notFound("Blocked period not found"));
        if (!b.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This blocked period belongs to another vendor");
        }
        blocked.delete(b);
    }
}
