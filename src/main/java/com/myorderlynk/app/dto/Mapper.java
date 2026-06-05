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

    public VendorDtos.VendorResponse vendor(Vendor v) {
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(), v.getCity(), v.getCountry(),
                v.getWhatsappNumber(), v.getInstagramHandle(), v.getLogoUrl(), v.getStoreSlug(),
                v.getVerificationStatus(), v.getFulfillmentTypes(), v.isActive(), v.getRating(),
                v.getCommissionRate());
    }

    public ProductDtos.ProductResponse product(Product p) {
        return new ProductDtos.ProductResponse(
                p.getId(), p.getVendorId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getCurrency(), p.getQuantityAvailable(), p.getProductImageUrl(),
                p.getFulfillmentType(), p.getOriginCountry(), p.isAvailableNow(), p.getBatchId(),
                p.isActive());
    }

    public OrderDtos.OrderItemResponse orderItem(OrderItem i) {
        return new OrderDtos.OrderItemResponse(
                i.getProductId(), i.getProductNameSnapshot(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
    }

    public OrderDtos.OrderResponse order(Order o, String vendorName) {
        List<OrderDtos.OrderItemResponse> items = o.getItems().stream().map(this::orderItem).toList();
        return new OrderDtos.OrderResponse(
                o.getId(), o.getPublicOrderId(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerCity(), o.getVendorId(), vendorName, items,
                o.getProductSubtotal(), o.getLogisticsFee(), o.getPlatformFee(), o.getProcessingFee(),
                o.getTotalAmount(), o.getVendorPayable(), o.getLogisticsPayable(), o.getPlatformRevenue(),
                o.getRefundedAmount(), o.getCurrency(), o.getPaymentStatus(), o.getFulfillmentType(), o.getFulfillmentStatus(),
                FulfillmentFlows.flowFor(o.getFulfillmentType()), o.getPickupCode(), o.getSourceChannel(),
                o.getCampaign(), o.getNotes(), o.getCreatedAt());
    }

    public PayoutDtos.PayoutResponse payout(Payout p) {
        return new PayoutDtos.PayoutResponse(
                p.getId(), p.getVendorId(), p.getPeriodStart(), p.getPeriodEnd(), p.getGrossSales(),
                p.getPlatformFees(), p.getLogisticsFees(), p.getRefunds(), p.getNetPayout(),
                p.getPayoutStatus(), p.getPaidDate());
    }
}
