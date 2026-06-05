package com.myorderlynk.app.web;

import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.domain.enums.ProductCategory;
import com.myorderlynk.app.domain.enums.SourceChannel;
import com.myorderlynk.app.service.FulfillmentFlows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Exposes option sets and fulfillment flows so the frontend never hard-codes enums. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @GetMapping("/option-sets")
    public Map<String, Object> optionSets() {
        Map<String, Object> sets = new LinkedHashMap<>();
        sets.put("fulfillmentTypes", names(FulfillmentType.values()));
        sets.put("fulfillmentStatuses", names(FulfillmentStatus.values()));
        sets.put("paymentStatuses", names(PaymentStatus.values()));
        sets.put("paymentMethods", names(PaymentMethod.values()));
        sets.put("productCategories", names(ProductCategory.values()));
        sets.put("sourceChannels", names(SourceChannel.values()));
        return sets;
    }

    @GetMapping("/fulfillment-flows")
    public Map<FulfillmentType, Object> fulfillmentFlows() {
        return Arrays.stream(FulfillmentType.values())
                .collect(Collectors.toMap(t -> t,
                        t -> FulfillmentFlows.flowFor(t).stream().map(Enum::name).toList(),
                        (a, b) -> a, () -> new LinkedHashMap<>()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
