package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchCard;
import com.myorderlynk.app.batch.BatchDtos.BatchProductResponse;
import com.myorderlynk.app.batch.BatchDtos.PublicBatchResponse;
import com.myorderlynk.app.common.enums.BatchStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.vendor.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Public Batch &amp; Cargo discovery (spec §9 Customer Batch Page, §10 Marketplace): marketplace batch
 * cards with filters, and a provider's batch page with its available products.
 */
@Service
public class BatchDiscoveryService {

    private static final List<BatchStatus> OPEN_STATUSES = List.of(BatchStatus.OPEN, BatchStatus.CLOSING_SOON);

    private final BatchRepository batches;
    private final BatchProductRepository batchProducts;
    private final VendorRepository vendors;
    private final BatchMapper mapper;

    public BatchDiscoveryService(BatchRepository batches, BatchProductRepository batchProducts,
                                 VendorRepository vendors, BatchMapper mapper) {
        this.batches = batches;
        this.batchProducts = batchProducts;
        this.vendors = vendors;
        this.mapper = mapper;
    }

    /** Marketplace-visible, open batches, optionally filtered by route endpoints and type. */
    @Transactional(readOnly = true)
    public List<BatchCard> marketplace(String originCountry, String destinationCity, BatchType batchType) {
        return batches.findByVisibilityAndBatchStatusInOrderByCloseDateAsc(BatchVisibility.MARKETPLACE, OPEN_STATUSES)
                .stream()
                .filter(b -> originCountry == null || originCountry.isBlank()
                        || originCountry.equalsIgnoreCase(b.getOriginCountry()))
                .filter(b -> destinationCity == null || destinationCity.isBlank()
                        || destinationCity.equalsIgnoreCase(b.getDestinationCity()))
                .filter(b -> batchType == null || b.getBatchType() == batchType)
                .map(this::card)
                .toList();
    }

    /** A provider's public batch page (cycle + available products). */
    @Transactional(readOnly = true)
    public PublicBatchResponse batchPage(UUID batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch not found"));
        if (b.getVisibility() == BatchVisibility.DRAFT) {
            throw ApiException.notFound("Batch not found");
        }
        Vendor v = vendors.findById(b.getVendorId()).orElse(null);
        List<BatchProductResponse> products = batchProducts.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(p -> p.getStatus() != BatchProductStatus.HIDDEN)
                .map(mapper::batchProduct)
                .toList();
        return new PublicBatchResponse(
                mapper.batch(b, v == null ? "Vendor" : v.getBusinessName()),
                v == null ? null : v.getStoreSlug(),
                v == null ? null : v.getWhatsappNumber(),
                products);
    }

    private BatchCard card(Batch b) {
        Vendor v = vendors.findById(b.getVendorId()).orElse(null);
        long productCount = batchProducts.findByBatchIdOrderByCreatedAtAsc(b.getId()).stream()
                .filter(p -> p.getStatus() == BatchProductStatus.AVAILABLE)
                .count();
        return new BatchCard(
                b.getId(), b.getVendorId(), v == null ? "Vendor" : v.getBusinessName(),
                v == null ? null : v.getStoreSlug(), b.getBatchName(), b.getBatchType(), b.getRoute(),
                b.getOriginCountry(), b.getDestinationCity(), b.getShippingMethod(), b.getCloseDate(),
                b.getEstimatedArrival(), b.getRatePerKg(), b.getCurrency(), (int) productCount,
                b.getBatchType() != BatchType.PRODUCT_BATCH, BatchMapper.openForOrders(b));
    }
}
