package com.myorderlynk.app.shipping;

/**
 * Raised by a {@link ShippingProvider} when a carrier call fails or the provider is not
 * configured. The service layer translates this into an appropriate API error.
 */
public class ShippingException extends RuntimeException {

    public ShippingException(String message) {
        super(message);
    }

    public ShippingException(String message, Throwable cause) {
        super(message, cause);
    }
}