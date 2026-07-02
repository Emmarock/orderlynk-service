package com.myorderlynk.app.catalog;
import com.myorderlynk.app.integration.ImageUploads;
import com.myorderlynk.app.integration.VideoUploads;
import com.myorderlynk.app.integration.OpenAiService;
import com.myorderlynk.app.integration.S3StorageService;

import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.catalog.ProductDtos.ProductRequest;
import com.myorderlynk.app.catalog.ProductDtos.ProductResponse;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Service
@Slf4j
public class ProductService {

    private static final int MAX_DESCRIPTION_WORDS = 100;

    private final ProductRepository products;
    private final ProductMapper mapper;
    private final S3StorageService storage;
    private final OpenAiService openAi;

    public ProductService(ProductRepository products, ProductMapper mapper, S3StorageService storage, OpenAiService openAi) {
        this.products = products;
        this.mapper = mapper;
        this.storage = storage;
        this.openAi = openAi;
    }

    /**
     * Generate a captivating, marketing-grade product description (under 100 words)
     * from the product name via OpenAI. The word limit is enforced server-side as a
     * safety net regardless of what the model returns.
     */
    public String generateDescription(String name, ProductCategory category) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Enter a product name first");
        }
        String system = "You are an expert e-commerce copywriter. Write a single, captivating product "
                + "description for an online storefront. It MUST be fewer than 100 words. Use vivid, "
                + "persuasive, sensory language that builds desire and drives the shopper to buy. Output "
                + "plain prose only — no markdown, no headings, no bullet points, no surrounding quotes, "
                + "and do not repeat the product name as a title.";
        String categoryHint = category == null ? "" : " Category: " + category.name().replace('_', ' ').toLowerCase() + ".";
        String user = "Product name: " + name.trim() + "." + categoryHint + " Write the description.";

        log.info("Generating AI description for product '{}' (category={})", name.trim(), category);
        String text = openAi.complete(system, user, 220, 0.85);
        String result = limitWords(stripWrappingQuotes(text), MAX_DESCRIPTION_WORDS);
        log.debug("Generated description ({} chars) for '{}'", result.length(), name.trim());
        return result;
    }

    private static String stripWrappingQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static String limitWords(String text, int maxWords) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) {
            return text.trim();
        }
        // Truncate to the limit and tidy trailing punctuation so it ends cleanly.
        String truncated = String.join(" ", Arrays.copyOf(words, maxWords));
        return truncated.replaceAll("[\\s,;:]+$", "") + "…";
    }

    /**
     * Store a product image uploaded from the vendor's device in S3 and return its
     * public URL. The vendor saves this URL as the product's {@code productImageUrl}.
     */
    public String uploadProductImage(UUID vendorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No image file was provided");
        }
        String contentType = file.getContentType();
        String ext = ImageUploads.extensionOrThrow(contentType);
        String key = "products/" + vendorId + "/" + UUID.randomUUID() + "." + ext;
        log.info("Uploading product image for vendor {}: type={} size={}B key={}",
                vendorId, contentType, file.getSize(), key);
        try {
            String url = storage.uploadPublic(file.getBytes(), contentType, key);
            log.info("Product image uploaded for vendor {} -> {}", vendorId, url);
            return url;
        } catch (IOException e) {
            log.error("Failed to read uploaded image for vendor {}", vendorId, e);
            throw ApiException.badRequest("Could not read the uploaded image");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listForVendor(UUID vendorId, Pageable pageable) {
        return PageResponse.of(products.findByVendorId(vendorId, pageable).map(mapper::product));
    }

    /** A vendor's low-stock products (bounded by how many a vendor reasonably stocks), lowest stock first. */
    @Transactional(readOnly = true)
    public java.util.List<ProductResponse> lowStockForVendor(UUID vendorId) {
        return products.findLowStockByVendor(vendorId).stream().map(mapper::product).toList();
    }

    @Transactional
    public ProductResponse create(UUID vendorId, ProductRequest req) {
        Product p = new Product();
        p.setVendorId(vendorId);
        apply(p, req);
        Product saved = products.save(p);
        log.info("Product created: {} '{}' for vendor {}", saved.getId(), saved.getName(), vendorId);
        return mapper.product(saved);
    }

    @Transactional
    public ProductResponse update(UUID vendorId, UUID productId, ProductRequest req) {
        Product p = ownedProduct(vendorId, productId);
        apply(p, req);
        log.info("Product updated: {} for vendor {}", productId, vendorId);
        return mapper.product(products.save(p));
    }

    @Transactional
    public void delete(UUID vendorId, UUID productId) {
        Product p = ownedProduct(vendorId, productId);
        products.delete(p);
        log.info("Product deleted: {} for vendor {}", productId, vendorId);
    }

    @Transactional
    public ProductResponse toggleActive(UUID vendorId, UUID productId, boolean active) {
        Product p = ownedProduct(vendorId, productId);
        p.setActive(active);
        log.info("Product {} set active={} for vendor {}", productId, active, vendorId);
        return mapper.product(products.save(p));
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.category() != null) p.setCategory(req.category());
        p.setPrice(req.price());
        if (req.discountPercent() != null) p.setDiscountPercent(req.discountPercent());
        if (req.currency() != null && !req.currency().isBlank()) p.setCurrency(req.currency());
        p.setQuantityAvailable(req.quantityAvailable());
        if (req.lowStockThreshold() != null) p.setLowStockThreshold(req.lowStockThreshold());
        applyMedia(p, req);
        p.setColors(normalizeOptions(req.colors()));
        p.setSizes(normalizeOptions(req.sizes()));
        p.setFulfillmentType(req.fulfillmentType());
        p.setOriginCountry(req.originCountry());
        p.setWeight(req.weight());
        if (req.weightUnit() != null) p.setWeightUnit(req.weightUnit());
        p.setLength(req.length());
        p.setWidth(req.width());
        p.setHeight(req.height());
        if (req.dimensionUnit() != null) p.setDimensionUnit(req.dimensionUnit());
        if (req.availableNow() != null) p.setAvailableNow(req.availableNow());
        p.setBatchId(req.batchId());
        if (req.active() != null) p.setActive(req.active());
    }

    /**
     * Resolve and validate the product's media: an ordered list of 1–6 images (first = cover)
     * plus an optional single video. Falls back to the legacy single {@code productImageUrl}
     * when {@code imageUrls} is absent so older clients keep working. The cover is denormalized
     * onto {@link Product#setProductImageUrl} (= {@code imageUrls[0]}) for card/thumbnail views.
     */
    private void applyMedia(Product p, ProductRequest req) {
        java.util.List<String> images = new java.util.ArrayList<>();
        if (req.imageUrls() != null && !req.imageUrls().isEmpty()) {
            for (String url : req.imageUrls()) {
                if (url != null && !url.isBlank()) images.add(url.trim());
            }
        } else if (req.productImageUrl() != null && !req.productImageUrl().isBlank()) {
            images.add(req.productImageUrl().trim());
        }
        if (images.isEmpty()) {
            throw ApiException.badRequest("Add at least one product image");
        }
        if (images.size() > 6) {
            throw ApiException.badRequest("A product can have at most 6 images");
        }
        p.setImageUrls(images);
        p.setProductImageUrl(images.get(0));
        String video = req.videoUrl();
        p.setVideoUrl(video != null && !video.isBlank() ? video.trim() : null);
    }

    /**
     * Clean a list of variant option labels (colours or sizes): trim, drop blanks, cap each label's
     * length, and de-duplicate case-insensitively while preserving the vendor's ordering and the
     * first-seen casing. Null/absent input yields an empty list (product has no such option).
     */
    private static java.util.List<String> normalizeOptions(java.util.List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String value : raw) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() > 64) trimmed = trimmed.substring(0, 64).trim();
            if (seen.add(trimmed.toLowerCase())) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }

    /**
     * Store a product video uploaded from the vendor's device in S3 and return its
     * public URL. The vendor saves this URL as the product's {@code videoUrl}.
     */
    public String uploadProductVideo(UUID vendorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No video file was provided");
        }
        String contentType = file.getContentType();
        String ext = VideoUploads.extensionOrThrow(contentType);
        String key = "products/" + vendorId + "/videos/" + UUID.randomUUID() + "." + ext;
        log.info("Uploading product video for vendor {}: type={} size={}B key={}",
                vendorId, contentType, file.getSize(), key);
        try {
            String url = storage.uploadPublic(file.getBytes(), contentType, key);
            log.info("Product video uploaded for vendor {} -> {}", vendorId, url);
            return url;
        } catch (IOException e) {
            log.error("Failed to read uploaded video for vendor {}", vendorId, e);
            throw ApiException.badRequest("Could not read the uploaded video");
        }
    }

    private Product ownedProduct(UUID vendorId, UUID productId) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!p.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This product belongs to another vendor");
        }
        return p;
    }
}
