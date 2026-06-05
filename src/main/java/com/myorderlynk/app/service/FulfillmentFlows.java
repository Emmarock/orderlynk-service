package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.FulfillmentType;

import java.util.List;
import java.util.Map;

import static com.myorderlynk.app.domain.enums.FulfillmentStatus.*;

/**
 * The ordered fulfillment status flow for each fulfillment type (PRD §12).
 */
public final class FulfillmentFlows {

    private static final Map<FulfillmentType, List<FulfillmentStatus>> FLOWS = Map.of(
            FulfillmentType.LOCAL_PICKUP, List.of(
                    ORDER_RECEIVED, PAID, VENDOR_CONFIRMED, READY_FOR_PICKUP, COMPLETED),
            FulfillmentType.LOCAL_DELIVERY, List.of(
                    ORDER_RECEIVED, PAID, VENDOR_CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, COMPLETED),
            FulfillmentType.DOMESTIC_SHIPPING, List.of(
                    ORDER_RECEIVED, PAID, VENDOR_CONFIRMED, PACKED, SHIPPED, DELIVERED, COMPLETED),
            FulfillmentType.IMPORT_BATCH, List.of(
                    ORDER_RECEIVED, PAID, ASSIGNED_TO_BATCH, SOURCING, PACKED, SHIPPED, ARRIVED, READY_FOR_PICKUP, COMPLETED),
            FulfillmentType.EXPORT_BATCH, List.of(
                    ORDER_RECEIVED, PAID, PREPARING, SHIPPED, ARRIVED, DELIVERED, COMPLETED));

    private FulfillmentFlows() {
    }

    public static List<FulfillmentStatus> flowFor(FulfillmentType type) {
        return FLOWS.getOrDefault(type, FLOWS.get(FulfillmentType.LOCAL_PICKUP));
    }

    /** A transition is valid if the target appears in the flow and is not a backward move (cancel is always allowed). */
    public static boolean isValidTransition(FulfillmentType type, FulfillmentStatus from, FulfillmentStatus to) {
        if (to == CANCELLED) {
            return from != COMPLETED;
        }
        List<FulfillmentStatus> flow = flowFor(type);
        int fromIdx = flow.indexOf(from);
        int toIdx = flow.indexOf(to);
        if (toIdx < 0) {
            return false;
        }
        // Allow staying at ORDER_RECEIVED start even if 'from' is CANCELLED-not-applicable.
        return fromIdx < 0 || toIdx >= fromIdx;
    }
}
