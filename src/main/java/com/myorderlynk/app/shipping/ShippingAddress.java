package com.myorderlynk.app.shipping;

/**
 * Provider-neutral postal address used when requesting rates and buying labels. Carriers
 * generally require at least street1, city, state (US/CA), zip and country; name and phone
 * improve label quality and delivery success.
 */
public record ShippingAddress(
        String name,
        String company,
        String street1,
        String street2,
        String city,
        String state,
        String zip,
        String country,
        String phone,
        String email,
        boolean residential) {

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder so callers can populate the many optional fields readably. */
    public static final class Builder {
        private String name;
        private String company;
        private String street1;
        private String street2;
        private String city;
        private String state;
        private String zip;
        private String country;
        private String phone;
        private String email;
        private boolean residential;

        public Builder name(String v) { this.name = v; return this; }
        public Builder company(String v) { this.company = v; return this; }
        public Builder street1(String v) { this.street1 = v; return this; }
        public Builder street2(String v) { this.street2 = v; return this; }
        public Builder city(String v) { this.city = v; return this; }
        public Builder state(String v) { this.state = v; return this; }
        public Builder zip(String v) { this.zip = v; return this; }
        public Builder country(String v) { this.country = v; return this; }
        public Builder phone(String v) { this.phone = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder residential(boolean v) { this.residential = v; return this; }

        public ShippingAddress build() {
            return new ShippingAddress(name, company, street1, street2, city, state, zip, country, phone, email, residential);
        }
    }
}