package com.myorderlynk.app.order;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.vendor.Vendor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps {@link Order} entities to API response records. */
@Component
public class OrderMapper {

    private final OrderLinks orderLinks;

    public OrderMapper(OrderLinks orderLinks) {
        this.orderLinks = orderLinks;
    }

    private static Address orEmpty(Address a) {
        return a == null ? new Address() : a;
    }

    public OrderDtos.OrderItemResponse orderItem(OrderItem i) {
        return new OrderDtos.OrderItemResponse(
                i.getProductId(), i.getProductNameSnapshot(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
    }

    /** Order view without payment instructions or track token — for vendor/admin consoles. */
    public OrderDtos.OrderResponse order(Order o, String vendorName) {
        return buildOrder(o, vendorName, null, false);
    }

    /** Customer-facing order view: includes the vendor's payment instructions and a tracking token. */
    public OrderDtos.OrderResponse order(Order o, Vendor vendor) {
        String name = vendor == null ? "Vendor" : vendor.getBusinessName();
        return buildOrder(o, name, paymentInstructions(vendor), true);
    }

    /** Checkout view: same as {@link #order(Order, Vendor)} plus the card-payment client secret. */
    public OrderDtos.OrderResponse order(Order o, Vendor vendor, String clientSecret, String paymentReference) {
        OrderDtos.OrderResponse b = order(o, vendor);
        return new OrderDtos.OrderResponse(
                b.id(), b.publicOrderId(), b.customerName(), b.customerPhone(), b.customerEmail(),
                b.customerHouseNumber(), b.customerStreet(), b.customerCity(), b.customerState(),
                b.customerPostcode(), b.customerCountry(), b.vendorId(), b.vendorName(), b.items(),
                b.productSubtotal(), b.logisticsFee(), b.platformFee(), b.processingFee(),
                b.totalAmount(), b.vendorPayable(), b.logisticsPayable(), b.platformRevenue(),
                b.refundedAmount(), b.currency(), b.paymentStatus(), b.fulfillmentType(),
                b.fulfillmentStatus(), b.fulfillmentFlow(), b.pickupCode(), b.sourceChannel(),
                b.campaign(), b.notes(), b.createdAt(), b.paymentInstructions(), b.trackToken(),
                clientSecret, paymentReference);
    }

    private OrderDtos.OrderResponse buildOrder(Order o, String vendorName, OrderDtos.PaymentInstructions payment,
                                               boolean includeTrackToken) {
        List<OrderDtos.OrderItemResponse> items = o.getItems().stream().map(this::orderItem).toList();
        Address d = orEmpty(o.getDeliveryAddress());
        String trackToken = includeTrackToken ? orderLinks.trackToken(o) : null;
        return new OrderDtos.OrderResponse(
                o.getId(), o.getPublicOrderId(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), d.getHouseNumber(), d.getStreet(), d.getCity(),
                d.getState(), d.getPostcode(), d.getCountry(), o.getVendorId(), vendorName, items,
                o.getProductSubtotal(), o.getLogisticsFee(), o.getPlatformFee(), o.getProcessingFee(),
                o.getTotalAmount(), o.getVendorPayable(), o.getLogisticsPayable(), o.getPlatformRevenue(),
                o.getRefundedAmount(), o.getCurrency(), o.getPaymentStatus(), o.getFulfillmentType(), o.getFulfillmentStatus(),
                FulfillmentFlows.flowFor(o.getFulfillmentType()), o.getPickupCode(), o.getSourceChannel(),
                o.getCampaign(), o.getNotes(), o.getCreatedAt(), payment, trackToken, null, null);
    }

    /** Builds payment instructions from a vendor's payout config; null when not configured. */
    private OrderDtos.PaymentInstructions paymentInstructions(Vendor v) {
        if (v == null || v.getPayoutMethod() == null || v.getPayoutMethod().isBlank()) {
            return null;
        }
        return new OrderDtos.PaymentInstructions(
                v.getPayoutMethod(), v.getPayoutAccountName(), v.getPayoutBankName(),
                v.getPayoutAccountNumber(), v.getPayoutEmail(),
                v.getPayoutCurrency(), v.getPayoutSortCode(), v.getPayoutRoutingNumber(),
                v.getPayoutInstitutionNumber(), v.getPayoutTransitNumber(), v.getPayoutIban(),
                v.getPayoutBic(), v.getPayoutBankCode());
    }
}
