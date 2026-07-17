package com.myorderlynk.app.common;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable postal address value type, embedded into entities that need one
 * (vendor business address, an order's delivery snapshot, a customer's saved
 * addresses). Column names are set per-entity via {@code @AttributeOverride}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Address {

    private String houseNumber;
    private String street;
    private String city;
    /** State / province / region. Required by some carriers (e.g. US/CA) for shipping rate calculation. */
    private String state;
    private String postcode;
    private String country;

    public boolean isEmpty() {
        return isBlank(houseNumber) && isBlank(street) && isBlank(city)
                && isBlank(state) && isBlank(postcode) && isBlank(country);
    }

    /**
     * The fields a carrier needs to rate a shipment and sell a label, in customer-friendly names;
     * an empty list means this address is ship-ready. State/province is only required for countries
     * that use one for postal addressing (US/CA). Single source of truth shared by shipping-rate
     * validation and vendor pickup-address onboarding, so the two can never disagree.
     */
    public List<String> missingShippingFields() {
        List<String> missing = new ArrayList<>();
        if (isBlank(street) && isBlank(houseNumber)) missing.add("street");
        if (isBlank(city)) missing.add("city");
        if (isBlank(postcode)) missing.add("postcode");
        if (isBlank(country)) missing.add("country");
        if (requiresState(country) && isBlank(state)) missing.add("state/province");
        return missing;
    }

    /** True when this address carries every field a carrier needs to ship from/to it. */
    public boolean isShippable() {
        return missingShippingFields().isEmpty();
    }

    /** Countries whose postal addresses require a state/province for carrier rating (US, Canada). */
    public static boolean requiresState(String country) {
        if (country == null) return false;
        String c = country.trim().toUpperCase();
        return c.equals("US") || c.equals("USA") || c.equals("CA") || c.equals("CAN")
                || c.equals("UNITED STATES") || c.equals("CANADA");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}