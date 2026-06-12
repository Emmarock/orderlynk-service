package com.myorderlynk.app.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Reusable address payloads, shared by the customer address book (and available for any address use). */
public final class AddressDtos {

    private AddressDtos() {
    }

    public record AddressDto(
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country) {
    }

    /**
     * A single address-autocomplete suggestion: the structured parts to fill a form plus a
     * human-readable {@code formatted} label for the dropdown and a 0–1 {@code confidence} score.
     */
    public record AddressSuggestion(
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country,
            String formatted,
            Double confidence) {
    }

    public record CustomerAddressRequest(
            @Size(max = 60) String label,
            @NotNull @Valid AddressDto address,
            Boolean makeDefault) {
    }

    public record CustomerAddressResponse(
            UUID id,
            String label,
            AddressDto address,
            boolean isDefault) {
    }
}