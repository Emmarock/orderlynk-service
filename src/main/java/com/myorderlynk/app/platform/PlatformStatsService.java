package com.myorderlynk.app.platform;

import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.platform.PlatformDtos.PlatformStats;
import com.myorderlynk.app.vendor.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Computes the public, platform-wide headline metrics for the OrderLynk home page. */
@Service
public class PlatformStatsService {

    /** Fulfillment states we treat as "successfully delivered to the customer". */
    private static final List<FulfillmentStatus> FULFILLED =
            List.of(FulfillmentStatus.DELIVERED, FulfillmentStatus.COMPLETED);

    /** Terminal states an order can settle into, fulfilled or otherwise. */
    private static final List<FulfillmentStatus> TERMINAL =
            List.of(FulfillmentStatus.DELIVERED, FulfillmentStatus.COMPLETED, FulfillmentStatus.CANCELLED);

    private final OrderRepository orders;
    private final VendorRepository vendors;
    private final PlatformStatsProperties props;

    public PlatformStatsService(OrderRepository orders, VendorRepository vendors,
                                PlatformStatsProperties props) {
        this.orders = orders;
        this.vendors = vendors;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PlatformStats stats() {
        long ordersProcessed = orders.count();
        long verifiedVendors = vendors.countByActiveTrueAndVerificationStatus(VendorStatus.APPROVED);
        long citiesServed = vendors.countDistinctCities(VendorStatus.APPROVED);

        // Reliability proxy: of the orders that have reached a terminal state, the share that were
        // fulfilled rather than cancelled. With no settled orders yet we report 100% (nothing failed).
        long fulfilled = orders.countByFulfillmentStatusIn(FULFILLED);
        long terminal = orders.countByFulfillmentStatusIn(TERMINAL);
        double fulfillmentRate = terminal == 0
                ? 100.0
                : BigDecimal.valueOf(fulfilled * 100.0 / terminal)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        // Apply the configurable marketing floor: show max(real, floor) so the home page reads
        // convincingly while the platform is young; real numbers take over once they surpass it.
        return new PlatformStats(
                Math.max(ordersProcessed, props.getOrdersFloor()),
                Math.max(verifiedVendors, props.getVerifiedVendorsFloor()),
                Math.max(citiesServed, props.getCitiesFloor()),
                Math.max(fulfillmentRate, props.getFulfillmentRateFloor()));
    }
}