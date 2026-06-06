package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.OrderItem;
import com.myorderlynk.app.domain.Payout;
import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.service.FulfillmentFlows;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps domain entities to API response records. */
@Component
public class Mapper {

    /** Full vendor view, including private payout details — for the vendor's own console and admins. */
    public VendorDtos.VendorResponse vendor(Vendor v) {
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(), v.getCity(), v.getCountry(),
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
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(), v.getCity(), v.getCountry(),
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
                p.getPrice(), p.getCurrency(), p.getQuantityAvailable(), p.getLowStockThreshold(), lowStock,
                p.getProductImageUrl(), p.getFulfillmentType(), p.getOriginCountry(), p.isAvailableNow(),
                p.getBatchId(), p.isActive());
    }

    public OrderDtos.OrderItemResponse orderItem(OrderItem i) {
        return new OrderDtos.OrderItemResponse(
                i.getProductId(), i.getProductNameSnapshot(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
    }

    /** Order view without payment instructions — for vendor/admin consoles. */
    public OrderDtos.OrderResponse order(Order o, String vendorName) {
        return buildOrder(o, vendorName, null);
    }

    /** Customer-facing order view: includes the vendor's payment instructions so the customer can pay. */
    public OrderDtos.OrderResponse order(Order o, Vendor vendor) {
        String name = vendor == null ? "Vendor" : vendor.getBusinessName();
        return buildOrder(o, name, paymentInstructions(vendor));
    }

    private OrderDtos.OrderResponse buildOrder(Order o, String vendorName, OrderDtos.PaymentInstructions payment) {
        List<OrderDtos.OrderItemResponse> items = o.getItems().stream().map(this::orderItem).toList();
        return new OrderDtos.OrderResponse(
                o.getId(), o.getPublicOrderId(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerCity(), o.getVendorId(), vendorName, items,
                o.getProductSubtotal(), o.getLogisticsFee(), o.getPlatformFee(), o.getProcessingFee(),
                o.getTotalAmount(), o.getVendorPayable(), o.getLogisticsPayable(), o.getPlatformRevenue(),
                o.getRefundedAmount(), o.getCurrency(), o.getPaymentStatus(), o.getFulfillmentType(), o.getFulfillmentStatus(),
                FulfillmentFlows.flowFor(o.getFulfillmentType()), o.getPickupCode(), o.getSourceChannel(),
                o.getCampaign(), o.getNotes(), o.getCreatedAt(), payment);
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
