package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.dto.FinanceDtos.EarningsSummary;
import com.myorderlynk.app.dto.FinanceDtos.OrderEarning;
import com.myorderlynk.app.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vendor earnings, derived from order money fields (Appendix A):
 * <pre>
 * commission   = productSubtotal - vendorPayable      (the platform's cut, deducted from the vendor)
 * net per order = vendorPayable - refundedAmount
 * </pre>
 * Headline figures are realized — computed over PAID orders only — while the per-order
 * breakdown lists every order in the range. Tax is an optional withholding ({@code app.fees.tax-rate}).
 */
@Service
public class EarningsService {

    private final OrderRepository orders;
    private final FeeProperties fees;

    public EarningsService(OrderRepository orders, FeeProperties fees) {
        this.orders = orders;
        this.fees = fees;
    }

    @Transactional(readOnly = true)
    public EarningsSummary earnings(UUID vendorId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        List<Order> os = orders.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal processing = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        long paidOrders = 0;

        List<OrderEarning> lines = new ArrayList<>(os.size());
        for (Order o : os) {
            BigDecimal orderCommission = nz(o.getProductSubtotal()).subtract(nz(o.getVendorPayable()));
            BigDecimal refund = nz(o.getRefundedAmount());
            BigDecimal net = nz(o.getVendorPayable()).subtract(refund);
            lines.add(new OrderEarning(o.getPublicOrderId(), o.getCreatedAt(), o.getPaymentStatus(),
                    scale(o.getProductSubtotal()), scale(orderCommission), scale(refund), scale(net)));

            if (o.getPaymentStatus() == PaymentStatus.PAID) {
                paidOrders++;
                grossSales = grossSales.add(nz(o.getProductSubtotal()));
                commission = commission.add(orderCommission);
                processing = processing.add(nz(o.getProcessingFee()));
                refunds = refunds.add(refund);
            }
        }

        BigDecimal netBeforeTax = grossSales.subtract(commission).subtract(refunds);
        BigDecimal tax = scale(netBeforeTax.multiply(nz(fees.getTaxRate())));
        BigDecimal netPayout = scale(netBeforeTax.subtract(tax));

        return new EarningsSummary(
                scale(grossSales), scale(commission), scale(processing), scale(refunds),
                nz(fees.getTaxRate()), tax, netPayout, os.size(), paidOrders, "CAD", lines);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}