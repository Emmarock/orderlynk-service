package com.myorderlynk.app.shipping;

import com.goshippo.shippo_sdk.Shippo;
import com.goshippo.shippo_sdk.models.components.AddressCreateRequest;
import com.goshippo.shippo_sdk.models.components.AddressFrom;
import com.goshippo.shippo_sdk.models.components.AddressTo;
import com.goshippo.shippo_sdk.models.components.DistanceUnitEnum;
import com.goshippo.shippo_sdk.models.components.LabelFileTypeEnum;
import com.goshippo.shippo_sdk.models.components.ParcelCreateRequest;
import com.goshippo.shippo_sdk.models.components.Parcels;
import com.goshippo.shippo_sdk.models.components.Rate;
import com.goshippo.shippo_sdk.models.components.ResponseMessage;
import com.goshippo.shippo_sdk.models.components.ServiceLevelWithParent;
import com.goshippo.shippo_sdk.models.components.ShipmentCreateRequest;
import com.goshippo.shippo_sdk.models.components.Track;
import com.goshippo.shippo_sdk.models.components.TrackingStatus;
import com.goshippo.shippo_sdk.models.components.TrackingStatusEnum;
import com.goshippo.shippo_sdk.models.components.TransactionCreateRequest;
import com.goshippo.shippo_sdk.models.components.TransactionStatusEnum;
import com.goshippo.shippo_sdk.models.components.Transaction;
import com.goshippo.shippo_sdk.models.components.WeightUnitEnum;
import com.goshippo.shippo_sdk.models.errors.SDKError;
import com.goshippo.shippo_sdk.models.operations.CreateShipmentResponse;
import com.goshippo.shippo_sdk.models.operations.CreateTransactionRequestBody;
import com.goshippo.shippo_sdk.models.operations.CreateTransactionResponse;
import com.goshippo.shippo_sdk.models.operations.GetTrackResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link ShippingProvider} backed by <a href="https://docs.goshippo.com/">Shippo</a>.
 *
 * <p>Maps Orderlynk's provider-neutral models onto the Shippo SDK: a {@link ShipmentRequest}
 * becomes a Shippo shipment whose {@code rates} we expose; buying a label is a Shippo
 * transaction; tracking is the tracking-status API. Weight is sent in grams and dimensions in
 * centimetres (Orderlynk normalises before calling), so we always hand Shippo {@code g}/{@code cm}.
 *
 * <p>Degrades gracefully: with no API token the bean still loads, {@link #isConfigured()}
 * returns false, and calls throw {@link ShippingException} instead of crashing the app.
 */
@Slf4j
@Component
public class ShippoShippingProvider implements ShippingProvider {

    private final boolean configured;
    private final Shippo client;
    private final LabelFileTypeEnum labelFileType;

    public ShippoShippingProvider(ShippingProperties properties) {
        ShippingProperties.Shippo cfg = properties.getShippo();
        String token = cfg.getApiToken() == null ? "" : cfg.getApiToken().trim();
        this.configured = !token.isBlank();
        this.labelFileType = parseLabelType(cfg.getLabelFileType());
        if (!configured) {
            this.client = null;
            log.warn("Shipping (Shippo) is not configured (shipping.shippo.api-token unset) — "
                    + "live rates/labels are disabled and checkout falls back to the flat logistics fee.");
            return;
        }
        // Shippo expects `Authorization: ShippoToken <token>`; the SDK writes the header value verbatim.
        String header = token.startsWith("ShippoToken") ? token : "ShippoToken " + token;
        Shippo.Builder builder = Shippo.builder().apiKeyHeader(header);
        if (cfg.getApiVersion() != null && !cfg.getApiVersion().isBlank()) {
            builder.shippoApiVersion(cfg.getApiVersion().trim());
        }
        this.client = builder.build();
        log.info("Shippo shipping provider configured (apiVersion={}, labelFileType={}).",
                cfg.getApiVersion(), labelFileType);
    }

    @Override
    public String key() {
        return "shippo";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public List<ShippingRate> getRates(ShipmentRequest request) {
        requireConfigured();
        try {
            ShipmentCreateRequest create = ShipmentCreateRequest.builder()
                    .addressFrom(AddressFrom.of(address(request.from())))
                    .addressTo(AddressTo.of(address(request.to())))
                    .parcels(request.parcels().stream().map(this::parcel).toList())
                    .metadata(request.metadata() == null ? "" : request.metadata())
                    .async(false)
                    .build();

            CreateShipmentResponse response = client.shipments().create(create);
            com.goshippo.shippo_sdk.models.components.Shipment shipment = response.shipment()
                    .orElseThrow(() -> new ShippingException("Shippo returned no shipment"));
            List<ShippingRate> rates = shipment.rates().stream()
                    .map(r -> toRate(r, shipment.objectId()))
                    .collect(Collectors.toList());
            if (rates.isEmpty()) {
                String messages = shipment.messages().stream()
                        .map(ResponseMessage::text).flatMap(Optional::stream)
                        .collect(Collectors.joining("; "));
                log.warn("Shippo returned 0 rates for shipment {} ({})", shipment.objectId(), messages);
            }
            return rates;
        } catch (ShippingException e) {
            throw e;
        } catch (Exception e) {
            throw new ShippingException("Shippo rate request failed: " + errorDetail(e), e);
        }
    }

    @Override
    public ShippingLabel purchaseLabel(String rateId) {
        requireConfigured();
        if (rateId == null || rateId.isBlank()) {
            throw new ShippingException("A rate id is required to buy a label");
        }
        try {
            TransactionCreateRequest create = TransactionCreateRequest.builder()
                    .rate(rateId)
                    .labelFileType(labelFileType)
                    .async(false)
                    .build();
            CreateTransactionResponse response = client.transactions().create(CreateTransactionRequestBody.of(create));
            Transaction tx = response.transaction()
                    .orElseThrow(() -> new ShippingException("Shippo returned no transaction"));
            ShipmentStatus status = mapTransactionStatus(tx.status().orElse(null));
            String messages = tx.messages().map(list -> list.stream()
                    .map(ResponseMessage::text).flatMap(Optional::stream)
                    .collect(Collectors.joining("; "))).orElse(null);
            if (status == ShipmentStatus.FAILED) {
                log.warn("Shippo label purchase for rate {} returned status {} ({})", rateId, tx.status(), messages);
            }
            return new ShippingLabel(
                    tx.objectId().orElse(null),
                    status,
                    tx.trackingNumber().orElse(null),
                    tx.trackingUrlProvider().orElse(null),
                    tx.labelUrl().orElse(null),
                    tx.eta().map(ShippoShippingProvider::parseInstant).orElse(null),
                    messages);
        } catch (ShippingException e) {
            throw e;
        } catch (Exception e) {
            throw new ShippingException("Shippo label purchase failed: " + errorDetail(e), e);
        }
    }

    @Override
    public TrackingInfo track(String carrierToken, String trackingNumber) {
        requireConfigured();
        try {
            GetTrackResponse response = client.trackingStatus().get(carrierToken, trackingNumber);
            Track track = response.track()
                    .orElseThrow(() -> new ShippingException("Shippo returned no tracking data"));
            ShipmentStatus status = track.trackingStatus()
                    .map(TrackingStatus::status).map(ShippoShippingProvider::mapTrackingStatus)
                    .orElse(ShipmentStatus.UNKNOWN);
            List<TrackingEvent> events = new ArrayList<>();
            for (TrackingStatus h : track.trackingHistory()) {
                events.add(new TrackingEvent(
                        mapTrackingStatus(h.status()),
                        h.statusDetails(),
                        location(h),
                        h.statusDate().map(ShippoShippingProvider::parseInstant).orElse(null)));
            }
            return new TrackingInfo(
                    track.carrier(),
                    track.trackingNumber(),
                    status,
                    track.eta().map(ShippoShippingProvider::parseInstant).orElse(null),
                    events);
        } catch (ShippingException e) {
            throw e;
        } catch (Exception e) {
            throw new ShippingException("Shippo tracking lookup failed: " + errorDetail(e), e);
        }
    }

    /** Best-effort human-readable detail for an SDK failure, unwrapping Shippo's JSON error body. */
    private static String errorDetail(Throwable e) {
        if (e instanceof SDKError sdk) {
            byte[] body = sdk.body();
            String text = body != null && body.length > 0
                    ? new String(body, java.nio.charset.StandardCharsets.UTF_8)
                    : sdk.message();
            return "HTTP " + sdk.code() + ": " + text;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    // ---- mapping helpers ----

    private AddressCreateRequest address(ShippingAddress a) {
        AddressCreateRequest.Builder b = AddressCreateRequest.builder()
                .country(blankToNull(a.country()) == null ? "US" : a.country());
        if (notBlank(a.name())) b.name(a.name());
        if (notBlank(a.company())) b.company(a.company());
        if (notBlank(a.street1())) b.street1(a.street1());
        if (notBlank(a.street2())) b.street2(a.street2());
        if (notBlank(a.city())) b.city(a.city());
        if (notBlank(a.state())) b.state(a.state());
        if (notBlank(a.zip())) b.zip(a.zip());
        if (notBlank(a.phone())) b.phone(a.phone());
        if (notBlank(a.email())) b.email(a.email());
        b.isResidential(a.residential());
        return b.build();
    }

    private Parcels parcel(ShippingParcel p) {
        ParcelCreateRequest create = ParcelCreateRequest.builder()
                .massUnit(weightUnit(p.massUnit()))
                .weight(plain(p.weight()))
                .distanceUnit(distanceUnit(p.distanceUnit()))
                .length(plain(p.length()))
                .width(plain(p.width()))
                .height(plain(p.height()))
                .build();
        return Parcels.of(create);
    }

    private ShippingRate toRate(Rate r, String shipmentId) {
        ServiceLevelWithParent svc = r.servicelevel();
        return new ShippingRate(
                r.objectId(),
                shipmentId,
                r.provider(),
                svc.name().orElse(r.provider()),
                svc.token().orElse(null),
                parseAmount(r.amount()),
                r.currency(),
                r.estimatedDays().map(Long::intValue).orElse(null),
                r.durationTerms().orElse(null),
                r.providerImage75().orElse(null));
    }

    private static ShipmentStatus mapTrackingStatus(TrackingStatusEnum status) {
        if (status == null) {
            return ShipmentStatus.UNKNOWN;
        }
        if (status.equals(TrackingStatusEnum.PRE_TRANSIT) || status.equals(TrackingStatusEnum.TRANSIT)) {
            return ShipmentStatus.IN_TRANSIT;
        }
        if (status.equals(TrackingStatusEnum.DELIVERED)) {
            return ShipmentStatus.DELIVERED;
        }
        if (status.equals(TrackingStatusEnum.RETURNED)) {
            return ShipmentStatus.RETURNED;
        }
        if (status.equals(TrackingStatusEnum.FAILURE)) {
            return ShipmentStatus.FAILED;
        }
        return ShipmentStatus.UNKNOWN;
    }

    private static ShipmentStatus mapTransactionStatus(TransactionStatusEnum status) {
        if (status == null) {
            return ShipmentStatus.UNKNOWN;
        }
        if (status.equals(TransactionStatusEnum.SUCCESS)) {
            return ShipmentStatus.PURCHASED;
        }
        if (status.equals(TransactionStatusEnum.ERROR)) {
            return ShipmentStatus.FAILED;
        }
        if (status.equals(TransactionStatusEnum.REFUNDED)
                || status.equals(TransactionStatusEnum.REFUNDPENDING)
                || status.equals(TransactionStatusEnum.REFUNDREJECTED)) {
            return ShipmentStatus.CANCELLED;
        }
        return ShipmentStatus.UNKNOWN; // WAITING / QUEUED
    }

    private static WeightUnitEnum weightUnit(WeightUnit unit) {
        return switch (unit == null ? WeightUnit.G : unit) {
            case G -> WeightUnitEnum.G;
            case KG -> WeightUnitEnum.KG;
            case OZ -> WeightUnitEnum.OZ;
            case LB -> WeightUnitEnum.LB;
        };
    }

    private static DistanceUnitEnum distanceUnit(DimensionUnit unit) {
        return switch (unit == null ? DimensionUnit.CM : unit) {
            case MM -> DistanceUnitEnum.MM;
            case CM -> DistanceUnitEnum.CM;
            case M -> DistanceUnitEnum.M;
            case IN -> DistanceUnitEnum.IN;
            case FT -> DistanceUnitEnum.FT;
            case YD -> DistanceUnitEnum.YD;
        };
    }

    private static String location(TrackingStatus h) {
        return h.location().map(loc -> {
            String city = loc.city().orElse(null);
            String state = loc.state().orElse(null);
            String country = loc.country().orElse(null);
            return java.util.stream.Stream.of(city, state, country)
                    .filter(ShippoShippingProvider::notBlank)
                    .collect(Collectors.joining(", "));
        }).filter(s -> !s.isBlank()).orElse(null);
    }

    private void requireConfigured() {
        if (!configured) {
            throw new ShippingException("Shippo is not configured (shipping.shippo.api-token is blank)");
        }
    }

    private LabelFileTypeEnum parseLabelType(String value) {
        if (value == null || value.isBlank()) {
            return LabelFileTypeEnum.PDF4X6;
        }
        String normalized = value.trim().toUpperCase().replace("X", "X").replace("-", "_");
        for (LabelFileTypeEnum t : LabelFileTypeEnum.values()) {
            if (t.name().equalsIgnoreCase(normalized) || t.value().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        log.warn("Unknown shipping.shippo.label-file-type '{}', defaulting to PDF4X6", value);
        return LabelFileTypeEnum.PDF4X6;
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return amount == null ? BigDecimal.ZERO : new BigDecimal(amount);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static Instant parseInstant(OffsetDateTime dt) {
        return dt == null ? null : dt.toInstant();
    }

    /** Parse a carrier-provided ISO timestamp string (e.g. a transaction ETA); null on any failure. */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /**
     * Shippo wants plain decimal strings with at most 4 decimal places (unit conversions can
     * produce more, e.g. 1.5 lb → 680.388555 g) and no scientific notation; null/zero become "0".
     */
    private static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}