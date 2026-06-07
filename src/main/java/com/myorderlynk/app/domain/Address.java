package com.myorderlynk.app.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}