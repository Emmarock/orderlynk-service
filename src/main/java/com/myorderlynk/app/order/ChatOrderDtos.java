package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Payloads for turning a pasted/forwarded chat thread (WhatsApp, Instagram, …) into a
 * structured draft order the vendor can review before creating it. The draft is advisory:
 * line items are matched against the vendor's catalogue, anything ambiguous is surfaced in
 * {@code unmatched} for a human to resolve. Nothing is persisted here — see
 * {@link ChatOrderParser}.
 */
public final class ChatOrderDtos {

    private ChatOrderDtos() {
    }

    /** Raw conversation text the vendor pasted in. */
    public record ParseChatRequest(@NotBlank String text) {
    }

    /** A catalogue-matched line. {@code confidence} is the model's 0–1 self-rating of the match. */
    public record DraftLine(
            UUID productId,
            String productName,
            int quantity,
            double confidence) {
    }

    /**
     * The structured order extracted from the chat. {@code items} are confidently matched to
     * real products; {@code unmatched} holds lines the model couldn't tie to the catalogue
     * (verbatim, e.g. "2x malva pudding") so the vendor can add or correct them by hand.
     */
    public record DraftOrder(
            List<DraftLine> items,
            List<String> unmatched,
            String customerName,
            String customerPhone,
            String customerEmail,
            FulfillmentType fulfillmentType,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            String notes) {
    }
}
