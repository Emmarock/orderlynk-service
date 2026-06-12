package com.myorderlynk.app.booking;

/** Where a service is delivered (PRD §13 location_type, §14 mobile-service edge case). */
public enum ServiceLocationType {
    /** Customer comes to the provider's premises (salon, shop, studio). */
    AT_PROVIDER,
    /** Provider travels to the customer; an address is collected at booking. */
    CUSTOMER_LOCATION,
    /** Remote/online service; no physical address needed. */
    REMOTE
}
