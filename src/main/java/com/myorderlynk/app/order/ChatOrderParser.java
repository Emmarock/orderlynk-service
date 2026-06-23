package com.myorderlynk.app.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorderlynk.app.catalog.ProductDtos.ProductResponse;
import com.myorderlynk.app.catalog.ProductService;
import com.myorderlynk.app.common.PageRequests;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.integration.OpenAiService;
import com.myorderlynk.app.order.ChatOrderDtos.DraftLine;
import com.myorderlynk.app.order.ChatOrderDtos.DraftOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns a freeform chat thread into a structured {@link DraftOrder} by prompting the LLM with
 * the vendor's own catalogue as grounding, then validating the result against that catalogue.
 *
 * <p>Reuses the existing {@link OpenAiService} transport (the project's single AI provider) so
 * there is one key and one place to swap models. The output is deliberately treated as advisory:
 * the model only ever proposes product IDs from the list we hand it, and every proposed ID is
 * re-checked here before it reaches the vendor. Lines the model can't match are returned verbatim
 * for a human to resolve — we never invent a product or a quantity.
 */
@Service
@Slf4j
public class ChatOrderParser {

    /** Catalogue size handed to the model. Clamped to the page cap; ample for a paste-to-order flow. */
    private static final int CATALOG_LIMIT = PageRequests.MAX_SIZE;

    private static final String SYSTEM_PROMPT = """
            You convert a customer chat conversation into a structured order for a vendor.
            You are given the vendor's product CATALOGUE (id + name) and the raw CHAT text.

            Return ONLY a JSON object, no prose and no markdown fences, matching exactly:
            {
              "items": [
                { "productId": "<an id copied verbatim from the CATALOGUE, or null if no confident match>",
                  "name": "<the item exactly as the customer wrote it>",
                  "quantity": <integer, default 1 when unstated>,
                  "confidence": <number 0..1 for how sure you are of the product match> }
              ],
              "customerName": "<name if stated, else null>",
              "customerPhone": "<phone if stated, else null>",
              "customerEmail": "<email if stated, else null>",
              "fulfillmentType": "<one of LOCAL_PICKUP, LOCAL_DELIVERY, DOMESTIC_SHIPPING, or null>",
              "address": {
                "houseNumber": "<unit/house number if stated, else null>",
                "street": "<street line if stated, else null>",
                "city": "<city if stated, else null>",
                "state": "<state/province if stated, else null>",
                "postcode": "<postal/zip code if stated, else null>",
                "country": "<country if stated, else null>"
              },
              "notes": "<anything important the vendor should see: timing, special requests, else null>"
            }

            Rules:
            - Only use productId values that appear in the CATALOGUE. If unsure, set productId to null — do not guess.
            - Account for the latest correction in the thread (e.g. "make it 3 instead") when setting quantity.
            - If a delivery/shipping address is in the chat, split it into the address fields; otherwise leave them null.
            - Never add items the customer did not ask for. Never fabricate a phone number or address.
            """;

    /** Read-only JSON parsing — ObjectMapper is thread-safe once configured, so a shared instance is fine. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAiService openAi;
    private final ProductService products;

    public ChatOrderParser(OpenAiService openAi, ProductService products) {
        this.openAi = openAi;
        this.products = products;
    }

    public DraftOrder parse(UUID vendorId, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Paste a chat conversation to parse");
        }

        List<ProductResponse> catalog =
                products.listForVendor(vendorId, PageRequests.of(0, CATALOG_LIMIT)).content();
        if (catalog.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add at least one product before parsing chat orders");
        }
        Map<UUID, ProductResponse> byId =
                catalog.stream().collect(Collectors.toMap(ProductResponse::id, p -> p));

        String catalogText = catalog.stream()
                .map(p -> "- " + p.id() + " | " + p.name())
                .collect(Collectors.joining("\n"));
        String userPrompt = "CATALOGUE:\n" + catalogText + "\n\nCHAT:\n" + rawText;

        // Low temperature: this is extraction, not creative writing.
        String json = stripFences(openAi.complete(SYSTEM_PROMPT, userPrompt, 1200, 0.0));

        RawParse raw;
        try {
            raw = MAPPER.readValue(json, RawParse.class);
        } catch (Exception e) {
            log.error("Chat-order parse returned non-JSON: {}", json, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read the order from that chat — try again");
        }

        List<DraftLine> items = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (RawLine line : raw.safeItems()) {
            UUID productId = parseUuid(line.productId());
            ProductResponse product = productId == null ? null : byId.get(productId);
            int qty = line.quantity() == null || line.quantity() < 1 ? 1 : line.quantity();
            if (product == null) {
                // Either no match proposed, or a hallucinated id not in this vendor's catalogue.
                unmatched.add(qty + "x " + (line.name() == null ? "(unspecified item)" : line.name()));
            } else {
                items.add(new DraftLine(product.id(), product.name(), qty, clamp(line.confidence())));
            }
        }

        RawAddress addr = raw.address() == null ? new RawAddress(null, null, null, null, null, null) : raw.address();
        return new DraftOrder(
                items,
                unmatched,
                blankToNull(raw.customerName()),
                blankToNull(raw.customerPhone()),
                blankToNull(raw.customerEmail()),
                parseFulfillment(raw.fulfillmentType()),
                blankToNull(addr.houseNumber()),
                blankToNull(addr.street()),
                blankToNull(addr.city()),
                blankToNull(addr.state()),
                blankToNull(addr.postcode()),
                blankToNull(addr.country()),
                blankToNull(raw.notes()));
    }

    /** Strip a ```json … ``` fence if the model wrapped its output despite instructions. */
    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static FulfillmentType parseFulfillment(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return FulfillmentType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double clamp(Double confidence) {
        if (confidence == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---- Loose shapes for the model's JSON; everything is validated above before use. ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawParse(
            List<RawLine> items,
            String customerName,
            String customerPhone,
            String customerEmail,
            String fulfillmentType,
            RawAddress address,
            String notes) {
        List<RawLine> safeItems() {
            return items == null ? List.of() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawLine(String productId, String name, Integer quantity, Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawAddress(String houseNumber, String street, String city, String state,
                              String postcode, String country) {
    }
}
