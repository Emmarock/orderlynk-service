package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.AttachProductsRequest;
import com.myorderlynk.app.batch.BatchDtos.BatchProductRequest;
import com.myorderlynk.app.batch.BatchDtos.BatchProductResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchRequest;
import com.myorderlynk.app.batch.BatchDtos.BatchResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchSummary;
import com.myorderlynk.app.batch.BatchDtos.CopyFromBatchRequest;
import com.myorderlynk.app.batch.BatchDtos.ManualPaymentRequest;
import com.myorderlynk.app.batch.BatchDtos.StatusUpdateRequest;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.OrderStatusUpdateRequest;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestResponse;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentStatusUpdateRequest;
import com.myorderlynk.app.batch.ShipmentRequestDtos.WeighRequest;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsVendor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Vendor/cargo-partner portal endpoints for Batch &amp; Cargo (spec §9 Vendor pages, §15). */
@RestController
@RequestMapping("/api/vendor")
@IsVendor
public class VendorBatchController {

    private final BatchService batchService;
    private final BatchOrderService batchOrders;
    private final ShipmentRequestService shipmentRequests;
    private final CurrentUser currentUser;

    public VendorBatchController(BatchService batchService, BatchOrderService batchOrders,
                                 ShipmentRequestService shipmentRequests, CurrentUser currentUser) {
        this.batchService = batchService;
        this.batchOrders = batchOrders;
        this.shipmentRequests = shipmentRequests;
        this.currentUser = currentUser;
    }

    // ---- Batch cycles ----

    @GetMapping("/batches")
    public PageResponse<BatchSummary> batches(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return batchService.listForVendor(vendorId(), page, size);
    }

    @GetMapping("/batches/{id}")
    public BatchSummary batch(@PathVariable UUID id) {
        return batchService.getForVendor(vendorId(), id);
    }

    @PostMapping("/batches")
    public BatchResponse createBatch(@Valid @RequestBody BatchRequest req) {
        return batchService.create(vendorId(), req);
    }

    @PutMapping("/batches/{id}")
    public BatchResponse updateBatch(@PathVariable UUID id, @Valid @RequestBody BatchRequest req) {
        return batchService.update(vendorId(), id, req);
    }

    @PostMapping("/batches/{id}/publish")
    public BatchResponse publish(@PathVariable UUID id, @RequestParam(required = false) BatchVisibility visibility) {
        return batchService.publish(vendorId(), id, visibility);
    }

    @PatchMapping("/batches/{id}/status")
    public BatchResponse updateBatchStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest req) {
        return batchService.updateStatus(vendorId(), id, req.status(), req.note());
    }

    @DeleteMapping("/batches/{id}")
    public void deleteBatch(@PathVariable UUID id) {
        batchService.delete(vendorId(), id);
    }

    // ---- Batch products ----

    @GetMapping("/batches/{id}/products")
    public List<BatchProductResponse> products(@PathVariable UUID id) {
        return batchService.listProducts(vendorId(), id);
    }

    @PostMapping("/batches/{id}/products")
    public List<BatchProductResponse> attachProducts(@PathVariable UUID id, @Valid @RequestBody AttachProductsRequest req) {
        return batchService.attachProducts(vendorId(), id, req.productIds());
    }

    @PostMapping("/batches/{id}/products/copy")
    public List<BatchProductResponse> copyProducts(@PathVariable UUID id, @Valid @RequestBody CopyFromBatchRequest req) {
        return batchService.copyFromBatch(vendorId(), id, req.sourceBatchId());
    }

    @PutMapping("/batches/{id}/products/{batchProductId}")
    public BatchProductResponse updateProduct(@PathVariable UUID id, @PathVariable UUID batchProductId,
                                              @Valid @RequestBody BatchProductRequest req) {
        return batchService.updateProduct(vendorId(), id, batchProductId, req);
    }

    @DeleteMapping("/batches/{id}/products/{batchProductId}")
    public void removeProduct(@PathVariable UUID id, @PathVariable UUID batchProductId) {
        batchService.removeProduct(vendorId(), id, batchProductId);
    }

    // ---- Batch orders ----

    @GetMapping("/batches/{id}/orders")
    public PageResponse<BatchOrderResponse> batchOrders(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return batchOrders.byBatch(vendorId(), id, page, size);
    }

    @GetMapping("/batch-orders")
    public PageResponse<BatchOrderResponse> allBatchOrders(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return batchOrders.forVendor(vendorId(), page, size);
    }

    @PatchMapping("/batch-orders/{id}/status")
    public BatchOrderResponse updateOrderStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusUpdateRequest req) {
        return batchOrders.updateStatus(vendorId(), id, req.status(), req.note(), actor());
    }

    /** Record a manual (card) payment for a batch order — only when manual payments are enabled. */
    @PostMapping("/batch-orders/{id}/payments")
    public BatchOrderResponse recordOrderPayment(@PathVariable UUID id, @Valid @RequestBody(required = false) ManualPaymentRequest req) {
        return batchOrders.recordManualPayment(vendorId(), id, req == null ? null : req.amount(),
                req == null ? null : req.reference(), actor());
    }

    // ---- Shipment requests ----

    @GetMapping("/batches/{id}/shipment-requests")
    public PageResponse<ShipmentRequestResponse> batchShipmentRequests(@PathVariable UUID id,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return shipmentRequests.byBatch(vendorId(), id, page, size);
    }

    @GetMapping("/shipment-requests")
    public PageResponse<ShipmentRequestResponse> allShipmentRequests(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return shipmentRequests.forVendor(vendorId(), page, size);
    }

    @PostMapping("/shipment-requests/{id}/receive")
    public ShipmentRequestResponse receive(@PathVariable UUID id) {
        return shipmentRequests.receive(vendorId(), id, actor());
    }

    @PostMapping("/shipment-requests/{id}/weigh")
    public ShipmentRequestResponse weigh(@PathVariable UUID id, @Valid @RequestBody WeighRequest req) {
        return shipmentRequests.weigh(vendorId(), id, req, actor());
    }

    @PatchMapping("/shipment-requests/{id}/status")
    public ShipmentRequestResponse updateShipmentStatus(@PathVariable UUID id,
                                                        @Valid @RequestBody ShipmentStatusUpdateRequest req) {
        return shipmentRequests.updateStatus(vendorId(), id, req.status(), req.note(), actor());
    }

    /** Record a manual (card) payment for a shipment request — only when manual payments are enabled. */
    @PostMapping("/shipment-requests/{id}/payments")
    public ShipmentRequestResponse recordShipmentPayment(@PathVariable UUID id, @Valid @RequestBody(required = false) ManualPaymentRequest req) {
        return shipmentRequests.recordManualPayment(vendorId(), id, req == null ? null : req.amount(),
                req == null ? null : req.reference(), actor());
    }

    private UUID vendorId() {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is linked to your account");
        }
        return vendorId;
    }

    private String actor() {
        AuthPrincipal me = currentUser.require();
        return "user:" + me.userId();
    }
}
