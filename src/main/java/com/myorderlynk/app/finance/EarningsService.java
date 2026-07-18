package com.myorderlynk.app.finance;
import com.myorderlynk.app.order.FeeSettingsService;

import com.myorderlynk.app.booking.Booking;
import com.myorderlynk.app.booking.BookingRepository;
import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.finance.FinanceDtos.EarningsSummary;
import com.myorderlynk.app.finance.FinanceDtos.OrderEarning;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.vendor.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final BookingRepository bookings;
    private final VendorRepository vendors;
    private final FeeSettingsService feeSettings;

    public EarningsService(OrderRepository orders, BookingRepository bookings,
                           VendorRepository vendors, FeeSettingsService feeSettings) {
        this.orders = orders;
        this.bookings = bookings;
        this.vendors = vendors;
        this.feeSettings = feeSettings;
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
        BigDecimal vatInPayout = BigDecimal.ZERO;

        List<Booking> bs = bookings.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end);
        long paidCount = 0;

        List<OrderEarning> lines = new ArrayList<>(os.size() + bs.size());
        for (Order o : os) {
            // VAT the vendor collects is a pass-through liability (remitted to government), not
            // earnings — exclude it so commission and net reflect what the vendor actually earns.
            // (vendorPayable already includes this VAT, since it is paid out to the vendor.)
            BigDecimal vendorVat = o.getVatCollector() == com.myorderlynk.app.common.enums.VatCollector.VENDOR
                    ? zeroIfNull(o.getVatAmount()) : BigDecimal.ZERO;
            BigDecimal orderCommission = zeroIfNull(o.getProductSubtotal())
                    .subtract(zeroIfNull(o.getVendorPayable()).subtract(vendorVat));
            BigDecimal refund = zeroIfNull(o.getRefundedAmount());
            BigDecimal net = zeroIfNull(o.getVendorPayable()).subtract(vendorVat).subtract(refund);
            lines.add(new OrderEarning(o.getPublicOrderId(), o.getCreatedAt(), o.getPaymentStatus(),
                    scale(o.getProductSubtotal()), scale(orderCommission), scale(refund), scale(net)));

            if (o.getPaymentStatus() == PaymentStatus.PAID) {
                paidCount++;
                grossSales = grossSales.add(zeroIfNull(o.getProductSubtotal()));
                commission = commission.add(orderCommission);
                processing = processing.add(zeroIfNull(o.getProcessingFee()));
                refunds = refunds.add(refund);
                // VAT the vendor collects rides inside vendorPayable, so it is transferred to the
                // vendor along with net earnings — surface it as an on-top addition, not earnings.
                vatInPayout = vatInPayout.add(vendorVat);
            }
        }

        // Service bookings: realized on the cash actually collected (deposits + balance), since
        // bookings are commonly paid in stages. Commission is the platform's cut at the vendor's rate.
        BigDecimal commissionRate = bs.isEmpty() ? BigDecimal.ZERO
                : vendors.findById(vendorId).map(Vendor::getCommissionRate).map(EarningsService::zeroIfNull).orElse(BigDecimal.ZERO);
        for (Booking b : bs) {
            BigDecimal collected = zeroIfNull(b.getAmountPaid());
            BigDecimal refund = zeroIfNull(b.getRefundedAmount());
            BigDecimal bookingCommission = scale(collected.multiply(commissionRate));
            BigDecimal net = collected.subtract(bookingCommission).subtract(refund);
            lines.add(new OrderEarning(b.getPublicBookingId(), b.getCreatedAt(), b.getPaymentStatus(),
                    scale(collected), scale(bookingCommission), scale(refund), scale(net)));

            if (collected.signum() > 0) {
                paidCount++;
                grossSales = grossSales.add(collected);
                commission = commission.add(bookingCommission);
                refunds = refunds.add(refund);
            }
        }

        lines.sort(Comparator.comparing(OrderEarning::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        BigDecimal netBeforeTax = grossSales.subtract(commission).subtract(refunds);
        BigDecimal taxRate = zeroIfNull(feeSettings.current().getTaxRate());
        BigDecimal tax = scale(netBeforeTax.multiply(taxRate));
        BigDecimal netPayout = scale(netBeforeTax.subtract(tax));

        return new EarningsSummary(
                scale(grossSales), scale(commission), scale(processing), scale(refunds),
                taxRate, tax, netPayout, scale(vatInPayout), os.size() + bs.size(), paidCount, "CAD", lines);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return zeroIfNull(v).setScale(2, RoundingMode.HALF_UP);
    }
}