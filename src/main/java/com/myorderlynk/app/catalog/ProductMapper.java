package com.myorderlynk.app.catalog;

import org.springframework.stereotype.Component;

/** Maps {@link Product} entities to API response records. */
@Component
public class ProductMapper {

    public ProductDtos.ProductResponse product(Product p) {
        boolean lowStock = p.getLowStockThreshold() > 0 && p.getQuantityAvailable() <= p.getLowStockThreshold();
        return new ProductDtos.ProductResponse(
                p.getId(), p.getVendorId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getDiscountPercent(), p.effectivePrice(), p.getCurrency(),
                p.getQuantityAvailable(), p.getLowStockThreshold(), lowStock,
                p.getProductImageUrl(), new java.util.ArrayList<>(p.getImageUrls()), p.getVideoUrl(),
                p.getFulfillmentType(), p.getOriginCountry(),
                p.getWeight(), p.getWeightUnit(), p.getLength(), p.getWidth(), p.getHeight(), p.getDimensionUnit(),
                p.isAvailableNow(), p.getBatchId(), p.isActive());
    }
}
