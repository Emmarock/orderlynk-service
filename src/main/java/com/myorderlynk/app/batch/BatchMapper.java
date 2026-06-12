package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchProductResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderItemResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderResponse;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestResponse;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.enums.BatchStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Maps Batch & Cargo entities to API response records. */
@Component
public class BatchMapper {

    private static Address orEmpty(Address a) {
        return a == null ? new Address() : a;
    }

    /** A batch is taking orders/requests when published and in an open status, before its close date. */
    public static boolean openForOrders(Batch b) {
        boolean open = b.getBatchStatus() == BatchStatus.OPEN || b.getBatchStatus() == BatchStatus.CLOSING_SOON;
        return open && b.getVisibility() != BatchVisibility.DRAFT && !b.isPastClose();
    }

    public BatchResponse batch(Batch b, String vendorName) {
        return new BatchResponse(
                b.getId(), b.getVendorId(), vendorName, b.getBatchName(), b.getBatchType(), b.getRoute(),
                b.getOriginCountry(), b.getOriginCity(), b.getDestinationCountry(), b.getDestinationCity(),
                b.getShippingMethod(), b.getOpenDate(), b.getCloseDate(), b.getEstimatedDeparture(),
                b.getEstimatedArrival(), b.getRatePerKg(), b.getHandlingFee(), b.getCurrency(),
                b.getPickupLocation(), List.copyOf(b.getCollectionPoints()), b.getBatchStatus(),
                b.getVisibility(), b.getNotes(), openForOrders(b));
    }

    public BatchProductResponse batchProduct(BatchProduct p) {
        return new BatchProductResponse(
                p.getId(), p.getBatchId(), p.getProductId(), p.getNameSnapshot(), p.getImageUrlSnapshot(),
                p.getDescription(), p.getBatchPrice(), p.getCurrency(), p.getQuantityLimit(),
                p.getSoldQuantity(), p.remaining(), p.getMinOrderQuantity(), p.getStatus(), p.getBatchNotes());
    }

    public BatchOrderItemResponse orderItem(BatchOrderItem i) {
        return new BatchOrderItemResponse(i.getBatchProductId(), i.getProductNameSnapshot(),
                i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
    }

    public BatchOrderResponse order(BatchOrder o, String batchName, String vendorName) {
        return order(o, batchName, vendorName, null, null);
    }

    public BatchOrderResponse order(BatchOrder o, String batchName, String vendorName,
                                    String clientSecret, String paymentReference) {
        Address a = orEmpty(o.getDeliveryAddress());
        List<BatchOrderItemResponse> items = new ArrayList<>();
        o.getItems().forEach(i -> items.add(orderItem(i)));
        return new BatchOrderResponse(
                o.getId(), o.getPublicOrderId(), o.getBatchId(), batchName, o.getVendorId(), vendorName,
                o.getCustomerUserId(), o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(), items,
                o.getFulfillmentType(), a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(),
                a.getPostcode(), a.getCountry(),
                o.getProductSubtotal(), o.getDeliveryFee(), o.getTotalAmount(), o.getAmountPaid(),
                o.balanceDue(), o.getRefundedAmount(), o.getCurrency(), o.getPaymentStatus(), o.getStatus(),
                o.getSourceChannel(), o.getPickupCode(), o.getNotes(), o.getCreatedAt(),
                clientSecret, paymentReference);
    }

    public ShipmentRequestResponse shipment(ShipmentRequest s, String batchName, String vendorName) {
        return shipment(s, batchName, vendorName, null, null);
    }

    public ShipmentRequestResponse shipment(ShipmentRequest s, String batchName, String vendorName,
                                            String clientSecret, String paymentReference) {
        return new ShipmentRequestResponse(
                s.getId(), s.getPublicRequestId(), s.getBatchId(), batchName, s.getVendorId(), vendorName,
                s.getCustomerUserId(), s.getCustomerName(), s.getCustomerPhone(), s.getCustomerEmail(),
                s.getItemDescription(), s.getPackageCount(), s.getEstimatedWeight(), s.getActualWeight(),
                s.getRatePerKg(), s.getHandlingFee(), s.getDeliveryFee(), s.getTotalCharge(),
                s.getAmountPaid(), s.balanceDue(), s.getRefundedAmount(), s.getCurrency(), s.getDeclaredValue(),
                s.isRestrictedItemsConfirmed(), s.getOriginDropOffLocation(), s.getDestinationLocation(),
                s.getDeliveryPreference(), s.getPaymentStatus(), s.getStatus(), s.getSourceChannel(),
                s.getPickupCode(), s.getNotes(), s.getCreatedAt(), clientSecret, paymentReference);
    }
}
