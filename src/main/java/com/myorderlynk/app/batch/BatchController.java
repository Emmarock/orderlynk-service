package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchCard;
import com.myorderlynk.app.batch.BatchDtos.PayRequest;
import com.myorderlynk.app.batch.BatchDtos.PaymentInitResponse;
import com.myorderlynk.app.batch.BatchDtos.PublicBatchResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderRequest;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderResponse;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestCreate;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestResponse;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsAuthenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public/customer Batch &amp; Cargo endpoints (spec §7, §9 Customer pages, §10 Marketplace). Browsing,
 * ordering, submitting shipment requests, tracking and paying are public (guest-friendly); the
 * "mine" history endpoints require a customer login.
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchDiscoveryService discovery;
    private final BatchOrderService batchOrders;
    private final ShipmentRequestService shipmentRequests;
    private final CurrentUser currentUser;

    public BatchController(BatchDiscoveryService discovery, BatchOrderService batchOrders,
                           ShipmentRequestService shipmentRequests, CurrentUser currentUser) {
        this.discovery = discovery;
        this.batchOrders = batchOrders;
        this.shipmentRequests = shipmentRequests;
        this.currentUser = currentUser;
    }

    // ---- Discovery ----

    @GetMapping
    public PageResponse<BatchCard> marketplace(@RequestParam(required = false) String originCountry,
                                               @RequestParam(required = false) String destinationCity,
                                               @RequestParam(required = false) BatchType batchType,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return discovery.marketplace(originCountry, destinationCity, batchType, page, size);
    }

    @GetMapping("/{id}")
    public PublicBatchResponse batchPage(@PathVariable UUID id) {
        return discovery.batchPage(id);
    }

    // ---- Batch product orders ----

    @PostMapping("/orders")
    public BatchOrderResponse createOrder(@Valid @RequestBody BatchOrderRequest req) {
        return batchOrders.create(req, currentUserId());
    }

    @PostMapping("/orders/track")
    public BatchOrderResponse trackOrder(@Valid @RequestBody TrackRequest req) {
        return batchOrders.track(req.publicId(), req.contact());
    }

    @GetMapping("/orders/mine")
    @IsAuthenticated
    public PageResponse<BatchOrderResponse> myOrders(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return batchOrders.customerOrders(currentUser.require().userId(), page, size);
    }

    @PostMapping("/orders/{publicId}/pay")
    public PaymentInitResponse payOrder(@PathVariable String publicId, @RequestBody(required = false) PayRequest req) {
        return batchOrders.initiatePayment(publicId, currentUserId(), req == null ? null : req.contact());
    }

    // ---- Shipment requests ----

    @PostMapping("/shipment-requests")
    public ShipmentRequestResponse createShipmentRequest(@Valid @RequestBody ShipmentRequestCreate req) {
        return shipmentRequests.create(req, currentUserId());
    }

    @PostMapping("/shipment-requests/track")
    public ShipmentRequestResponse trackShipmentRequest(@Valid @RequestBody TrackRequest req) {
        return shipmentRequests.track(req.publicId(), req.contact());
    }

    @GetMapping("/shipment-requests/mine")
    @IsAuthenticated
    public PageResponse<ShipmentRequestResponse> myShipmentRequests(@RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return shipmentRequests.customerRequests(currentUser.require().userId(), page, size);
    }

    @PostMapping("/shipment-requests/{publicId}/pay")
    public PaymentInitResponse payShipmentRequest(@PathVariable String publicId,
                                                  @RequestBody(required = false) PayRequest req) {
        return shipmentRequests.initiatePayment(publicId, currentUserId(), req == null ? null : req.contact());
    }

    private UUID currentUserId() {
        AuthPrincipal principal = currentUser.resolve();
        return principal == null ? null : principal.userId();
    }

    public record TrackRequest(@NotBlank String publicId, @NotBlank String contact) {
    }
}
