package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchProductRequest;
import com.myorderlynk.app.batch.BatchDtos.BatchProductResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchRequest;
import com.myorderlynk.app.batch.BatchDtos.BatchResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchSummary;
import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.common.enums.BatchStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.vendor.VendorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vendor-facing management of batch cycles and the products within them (spec §7.1, §11.1/§11.2,
 * §12). All write methods are scoped by vendor id. Products are reused from the catalog — never
 * recreated — and can be copied from a previous batch.
 */
@Service
@Slf4j
public class BatchService {

    private final BatchRepository batches;
    private final BatchProductRepository batchProducts;
    private final BatchOrderRepository batchOrders;
    private final ShipmentRequestRepository shipmentRequests;
    private final ProductRepository products;
    private final VendorRepository vendors;
    private final BatchMapper mapper;
    private final BatchNotificationService notifications;
    private final ZoneId lifecycleZone;
    private final int closingSoonDays;
    private final int closeHour;
    private final int sourcingDelayDays;

    /** Lifecycle statuses the scheduler auto-advances, in order. Vendor-set later stages are left alone. */
    private static final List<BatchStatus> AUTO_MANAGED = List.of(
            BatchStatus.OPEN, BatchStatus.CLOSING_SOON, BatchStatus.CLOSED, BatchStatus.SOURCING);

    public BatchService(BatchRepository batches, BatchProductRepository batchProducts,
                        BatchOrderRepository batchOrders, ShipmentRequestRepository shipmentRequests,
                        ProductRepository products, VendorRepository vendors, BatchMapper mapper,
                        BatchNotificationService notifications,
                        @Value("${app.batches.timezone:America/Toronto}") String batchTimezone,
                        @Value("${app.batches.closing-soon-days:5}") int closingSoonDays,
                        @Value("${app.batches.close-hour:18}") int closeHour,
                        @Value("${app.batches.sourcing-delay-days:2}") int sourcingDelayDays) {
        this.batches = batches;
        this.batchProducts = batchProducts;
        this.batchOrders = batchOrders;
        this.shipmentRequests = shipmentRequests;
        this.products = products;
        this.vendors = vendors;
        this.mapper = mapper;
        this.notifications = notifications;
        this.lifecycleZone = ZoneId.of(batchTimezone);
        this.closingSoonDays = closingSoonDays;
        this.closeHour = closeHour;
        this.sourcingDelayDays = sourcingDelayDays;
    }

    // ---- Batch cycles ----

    @Transactional(readOnly = true)
    public PageResponse<BatchSummary> listForVendor(UUID vendorId, int page, int size) {
        String name = vendorName(vendorId);
        List<BatchSummary> summaries = batches.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .map(b -> summary(b, name)).toList();
        return PageResponse.of(summaries, page, size);
    }

    @Transactional(readOnly = true)
    public BatchSummary getForVendor(UUID vendorId, UUID batchId) {
        return summary(owned(vendorId, batchId), vendorName(vendorId));
    }

    @Transactional
    public BatchResponse create(UUID vendorId, BatchRequest req) {
        Batch b = new Batch();
        b.setVendorId(vendorId);
        apply(b, req);
        Batch saved = batches.save(b);
        log.info("Batch created: {} '{}' for vendor {}", saved.getId(), saved.getBatchName(), vendorId);
        return mapper.batch(saved, vendorName(vendorId));
    }

    @Transactional
    public BatchResponse update(UUID vendorId, UUID batchId, BatchRequest req) {
        Batch b = owned(vendorId, batchId);
        // Pricing is locked once a batch is open for orders — customers may already have ordered
        // (or be about to) at the published rate/currency, so they can't change underneath them.
        if (b.getBatchStatus() == BatchStatus.OPEN
                && (moneyChanged(b.getRatePerKg(), req.ratePerKg())
                    || moneyChanged(b.getHandlingFee(), req.handlingFee())
                    || currencyChanged(b.getCurrency(), req.currency()))) {
            throw ApiException.badRequest(
                    "Rate per kg, handling fee and currency can't be changed while the batch is open for orders");
        }
        apply(b, req);
        return mapper.batch(batches.save(b), vendorName(vendorId));
    }

    private static boolean moneyChanged(BigDecimal current, BigDecimal incoming) {
        if (incoming == null) {
            return false; // field not supplied → not a change
        }
        return current == null || current.compareTo(incoming) != 0;
    }

    private static boolean currencyChanged(String current, String incoming) {
        return incoming != null && !incoming.isBlank() && !incoming.equalsIgnoreCase(current);
    }

    /** Publish a batch so customers can reach it: opens orders and makes it at least link-visible. */
    @Transactional
    public BatchResponse publish(UUID vendorId, UUID batchId, BatchVisibility visibility) {
        Batch b = owned(vendorId, batchId);
        b.setBatchStatus(BatchStatus.OPEN);
        b.setVisibility(visibility == null || visibility == BatchVisibility.DRAFT
                ? BatchVisibility.PRIVATE_LINK : visibility);
        log.info("Batch {} published ({}, {})", batchId, b.getBatchStatus(), b.getVisibility());
        return mapper.batch(batches.save(b), vendorName(vendorId));
    }

    @Transactional
    public BatchResponse updateStatus(UUID vendorId, UUID batchId, BatchStatus status, String note) {
        Batch b = owned(vendorId, batchId);
        BatchStatus from = b.getBatchStatus();
        b.setBatchStatus(status);
        batches.save(b);
        log.info("Batch {} status {} -> {} (by vendor {})", batchId, from, status, vendorId);
        notifyBatchCustomers(b, status);
        return mapper.batch(b, vendorName(vendorId));
    }

    @Transactional
    public void delete(UUID vendorId, UUID batchId) {
        Batch b = owned(vendorId, batchId);
        if (!batchOrders.findByBatchIdOrderByCreatedAtDesc(batchId).isEmpty()
                || !shipmentRequests.findByBatchIdOrderByCreatedAtDesc(batchId).isEmpty()) {
            throw ApiException.badRequest("This batch has orders or shipment requests and can't be deleted");
        }
        batchProducts.findByBatchIdOrderByCreatedAtAsc(batchId).forEach(batchProducts::delete);
        batches.delete(b);
        log.info("Batch {} deleted for vendor {}", batchId, vendorId);
    }

    // ---- Batch products ----

    @Transactional(readOnly = true)
    public List<BatchProductResponse> listProducts(UUID vendorId, UUID batchId) {
        owned(vendorId, batchId);
        return batchProducts.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .map(mapper::batchProduct).toList();
    }

    /** Attach catalog products to a batch, snapshotting name/image and defaulting the batch price. */
    @Transactional
    public List<BatchProductResponse> attachProducts(UUID vendorId, UUID batchId, List<UUID> productIds) {
        owned(vendorId, batchId);
        for (UUID productId : productIds) {
            Product p = products.findById(productId)
                    .orElseThrow(() -> ApiException.badRequest("Product " + productId + " not found"));
            if (!p.getVendorId().equals(vendorId)) {
                throw ApiException.forbidden("That product belongs to another vendor");
            }
            batchProducts.save(fromCatalog(batchId, vendorId, p));
        }
        log.info("Attached {} products to batch {}", productIds.size(), batchId);
        return listProducts(vendorId, batchId);
    }

    /** Copy every batch product from a previous batch into this one (spec §4.1, §7.1). */
    @Transactional
    public List<BatchProductResponse> copyFromBatch(UUID vendorId, UUID batchId, UUID sourceBatchId) {
        owned(vendorId, batchId);
        owned(vendorId, sourceBatchId);
        List<BatchProduct> source = batchProducts.findByBatchIdOrderByCreatedAtAsc(sourceBatchId);
        for (BatchProduct src : source) {
            BatchProduct copy = new BatchProduct();
            copy.setBatchId(batchId);
            copy.setVendorId(vendorId);
            copy.setProductId(src.getProductId());
            copy.setNameSnapshot(src.getNameSnapshot());
            copy.setImageUrlSnapshot(src.getImageUrlSnapshot());
            copy.setDescription(src.getDescription());
            copy.setBatchPrice(src.getBatchPrice());
            copy.setCurrency(src.getCurrency());
            copy.setQuantityLimit(src.getQuantityLimit());
            copy.setMinOrderQuantity(src.getMinOrderQuantity());
            copy.setBatchNotes(src.getBatchNotes());
            copy.setStatus(BatchProductStatus.AVAILABLE);
            batchProducts.save(copy);
        }
        log.info("Copied {} products from batch {} into {}", source.size(), sourceBatchId, batchId);
        return listProducts(vendorId, batchId);
    }

    @Transactional
    public BatchProductResponse updateProduct(UUID vendorId, UUID batchId, UUID batchProductId, BatchProductRequest req) {
        BatchProduct bp = ownedProduct(vendorId, batchId, batchProductId);
        if (req.batchPrice() != null) bp.setBatchPrice(req.batchPrice());
        if (req.quantityLimit() != null) bp.setQuantityLimit(req.quantityLimit());
        if (req.minOrderQuantity() != null) bp.setMinOrderQuantity(req.minOrderQuantity());
        if (req.status() != null) bp.setStatus(req.status());
        bp.setBatchNotes(req.batchNotes());
        return mapper.batchProduct(batchProducts.save(bp));
    }

    @Transactional
    public void removeProduct(UUID vendorId, UUID batchId, UUID batchProductId) {
        batchProducts.delete(ownedProduct(vendorId, batchId, batchProductId));
    }

    // ---- automated lifecycle (scheduler) ----

    /**
     * Advances open-cycle batches by their configured close date (batch-cargo spec §8.1):
     * OPEN → CLOSING_SOON 5 days before close, → CLOSED at the close date 18:00 local, →
     * SOURCING 2 days after close. Forward-only and idempotent; vendor/admin-set later stages
     * (CONSOLIDATING, SHIPPED, …) and DRAFT/DELAYED batches are left untouched. Returns #changed.
     */
    @Transactional
    public int advanceLifecycle() {
        Instant now = Instant.now();
        int changed = 0;
        for (Batch b : batches.findByBatchStatusInAndCloseDateNotNull(AUTO_MANAGED)) {
            BatchStatus target = targetStatus(now, b.getCloseDate());
            if (rank(target) > rank(b.getBatchStatus())) {
                BatchStatus from = b.getBatchStatus();
                b.setBatchStatus(target);
                batches.save(b);
                notifyBatchCustomers(b, target);
                changed++;
                log.info("Batch {} auto-advanced {} -> {} (close {})", b.getId(), from, target, b.getCloseDate());
            }
        }
        if (changed > 0) {
            log.info("Batch lifecycle pass advanced {} batch(es)", changed);
        }
        return changed;
    }

    /** The status a batch should hold given the current time and its close date. */
    private BatchStatus targetStatus(Instant now, LocalDate closeDate) {
        Instant closingSoonAt = closeDate.minusDays(closingSoonDays).atStartOfDay(lifecycleZone).toInstant();
        Instant closeAt = closeDate.atTime(LocalTime.of(closeHour, 0)).atZone(lifecycleZone).toInstant();
        Instant sourcingAt = closeAt.plus(Duration.ofDays(sourcingDelayDays));
        if (!now.isBefore(sourcingAt)) return BatchStatus.SOURCING;
        if (!now.isBefore(closeAt)) return BatchStatus.CLOSED;
        if (!now.isBefore(closingSoonAt)) return BatchStatus.CLOSING_SOON;
        return BatchStatus.OPEN;
    }

    /** Position of a status in the auto-managed progression; -1 if not auto-managed. */
    private static int rank(BatchStatus status) {
        return AUTO_MANAGED.indexOf(status);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public PageResponse<BatchSummary> adminListAll(int page, int size) {
        List<BatchSummary> summaries = batches.findAll().stream()
                .map(b -> summary(b, vendorName(b.getVendorId()))).toList();
        return PageResponse.of(summaries, page, size);
    }

    @Transactional(readOnly = true)
    public BatchSummary adminGet(UUID batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch not found"));
        return summary(b, vendorName(b.getVendorId()));
    }

    @Transactional
    public BatchResponse adminUpdateStatus(UUID batchId, BatchStatus status) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch not found"));
        b.setBatchStatus(status);
        batches.save(b);
        notifyBatchCustomers(b, status);
        log.info("Admin set batch {} status -> {}", batchId, status);
        return mapper.batch(b, vendorName(b.getVendorId()));
    }

    // ---- helpers ----

    private BatchProduct fromCatalog(UUID batchId, UUID vendorId, Product p) {
        BatchProduct bp = new BatchProduct();
        bp.setBatchId(batchId);
        bp.setVendorId(vendorId);
        bp.setProductId(p.getId());
        bp.setNameSnapshot(p.getName());
        bp.setImageUrlSnapshot(p.getProductImageUrl());
        bp.setDescription(p.getDescription());
        bp.setBatchPrice(p.effectivePrice());
        bp.setCurrency(p.getCurrency());
        bp.setMinOrderQuantity(1);
        bp.setStatus(BatchProductStatus.AVAILABLE);
        return bp;
    }

    private void apply(Batch b, BatchRequest req) {
        b.setBatchName(req.batchName());
        if (req.batchType() != null) b.setBatchType(req.batchType());
        b.setRoute(req.route());
        b.setOriginCountry(req.originCountry());
        b.setOriginCity(req.originCity());
        b.setDestinationCountry(req.destinationCountry());
        b.setDestinationCity(req.destinationCity());
        if (req.shippingMethod() != null) b.setShippingMethod(req.shippingMethod());
        b.setOpenDate(req.openDate());
        b.setCloseDate(req.closeDate());
        b.setEstimatedDeparture(req.estimatedDeparture());
        b.setEstimatedArrival(req.estimatedArrival());
        b.setRatePerKg(req.ratePerKg());
        if (req.handlingFee() != null) b.setHandlingFee(req.handlingFee());
        if (req.currency() != null && !req.currency().isBlank()) b.setCurrency(req.currency());
        b.setPickupLocation(req.pickupLocation());
        if (req.collectionPoints() != null) {
            b.getCollectionPoints().clear();
            b.getCollectionPoints().addAll(req.collectionPoints());
        }
        if (req.visibility() != null) b.setVisibility(req.visibility());
        b.setNotes(req.notes());
    }

    private BatchSummary summary(Batch b, String vendorName) {
        List<BatchOrder> orders = batchOrders.findByBatchIdOrderByCreatedAtDesc(b.getId());
        List<ShipmentRequest> reqs = shipmentRequests.findByBatchIdOrderByCreatedAtDesc(b.getId());
        int paid = (int) orders.stream().filter(o -> o.getPaymentStatus() == com.myorderlynk.app.common.enums.PaymentStatus.PAID).count();
        BigDecimal revenue = orders.stream().map(BatchOrder::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(reqs.stream().map(ShipmentRequest::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal pending = orders.stream().map(BatchOrder::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(reqs.stream().map(ShipmentRequest::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new BatchSummary(mapper.batch(b, vendorName), orders.size(), paid, reqs.size(), revenue, pending);
    }

    private void notifyBatchCustomers(Batch b, BatchStatus status) {
        notifications.notifyProvider("BATCH", b.getId(), "BATCH_STATUS_CHANGED",
                "Batch '" + b.getBatchName() + "' is now " + status);
        batchOrders.findByBatchIdOrderByCreatedAtDesc(b.getId()).forEach(o ->
                notifications.notifyCustomer("BATCH_ORDER", o.getId(), o.getCustomerEmail(), o.getCustomerPhone(),
                        "BATCH_STATUS_CHANGED", "Update on " + b.getBatchName() + ": status is now " + status + "."));
        shipmentRequests.findByBatchIdOrderByCreatedAtDesc(b.getId()).forEach(s ->
                notifications.notifyCustomer("SHIPMENT_REQUEST", s.getId(), s.getCustomerEmail(), s.getCustomerPhone(),
                        "BATCH_STATUS_CHANGED", "Update on " + b.getBatchName() + ": status is now " + status + "."));
    }

    Batch owned(UUID vendorId, UUID batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch not found"));
        if (!b.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This batch belongs to another vendor");
        }
        return b;
    }

    private BatchProduct ownedProduct(UUID vendorId, UUID batchId, UUID batchProductId) {
        BatchProduct bp = batchProducts.findById(batchProductId)
                .orElseThrow(() -> ApiException.notFound("Batch product not found"));
        if (!bp.getVendorId().equals(vendorId) || !bp.getBatchId().equals(batchId)) {
            throw ApiException.forbidden("That product belongs to another batch");
        }
        return bp;
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }
}
