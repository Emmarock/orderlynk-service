package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.Address;
import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.OrderItem;
import com.myorderlynk.app.domain.Payout;
import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.service.FulfillmentFlows;
import com.myorderlynk.app.service.OrderLinks;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps domain entities to API response records. */
@Component
public class Mapper {

    private final OrderLinks orderLinks;

    public Mapper(OrderLinks orderLinks) {
        this.orderLinks = orderLinks;
    }

    /** Hibernate maps an all-null @Embedded address to null on read; treat that as an empty address. */
    private static Address orEmpty(Address a) {
        return a == null ? new Address() : a;
    }

    /** Full vendor view, including private payout details — for the vendor's own console and admins. */
    public VendorDtos.VendorResponse vendor(Vendor v) {
        Address a = orEmpty(v.getAddress());
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(),
                a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(), a.getPostcode(), a.getCountry(),
                v.getWhatsappNumber(), v.getInstagramHandle(), v.getLogoUrl(), v.getBannerUrl(), v.getStoreSlug(),
                v.getVerificationStatus(), v.getFulfillmentTypes(), v.isActive(), v.getRating(),
                v.getRatingCount(), v.getCommissionRate(),
                v.getPayoutMethod(), v.getPayoutAccountName(), v.getPayoutAccountNumber(),
                v.getPayoutBankName(), v.getPayoutEmail(),
                v.isNotifyByEmail(), v.isNotifyByWhatsapp(), v.isLowStockAlerts());
    }

    /**
     * Public vendor view for marketplace/storefront — omits private payout details
     * (account numbers, e-transfer email) so they never leak to anonymous browsers.
     * Customers see payment details only on their own order (see {@link #order(Order, Vendor)}).
     */
    public VendorDtos.VendorResponse publicVendor(Vendor v) {
        Address a = orEmpty(v.getAddress());
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(),
                a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(), a.getPostcode(), a.getCountry(),
                v.getWhatsappNumber(), v.getInstagramHandle(), v.getLogoUrl(), v.getBannerUrl(), v.getStoreSlug(),
                v.getVerificationStatus(), v.getFulfillmentTypes(), v.isActive(), v.getRating(),
                v.getRatingCount(), v.getCommissionRate(),
                null, null, null, null, null,
                v.isNotifyByEmail(), v.isNotifyByWhatsapp(), v.isLowStockAlerts());
    }

    public ProductDtos.ProductResponse product(Product p) {
        boolean lowStock = p.getLowStockThreshold() > 0 && p.getQuantityAvailable() <= p.getLowStockThreshold();
        return new ProductDtos.ProductResponse(
                p.getId(), p.getVendorId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getDiscountPercent(), p.effectivePrice(), p.getCurrency(),
                p.getQuantityAvailable(), p.getLowStockThreshold(), lowStock,
                p.getProductImageUrl(), p.getFulfillmentType(), p.getOriginCountry(),
                p.getWeight(), p.getWeightUnit(), p.getLength(), p.getWidth(), p.getHeight(), p.getDimensionUnit(),
                p.isAvailableNow(), p.getBatchId(), p.isActive());
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
                v.getPayoutAccountNumber(), v.getPayoutEmail());
    }

    public PayoutDtos.PayoutResponse payout(Payout p) {
        return new PayoutDtos.PayoutResponse(
                p.getId(), p.getVendorId(), p.getPeriodStart(), p.getPeriodEnd(), p.getGrossSales(),
                p.getPlatformFees(), p.getLogisticsFees(), p.getRefunds(), p.getNetPayout(),
                p.getPayoutStatus(), p.getPaidDate());
    }
}
