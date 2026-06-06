package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.domain.enums.ProductCategory;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.ProductDtos.ProductRequest;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.repository.ProductRepository;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ProductService {

    private static final int MAX_DESCRIPTION_WORDS = 100;

    private final ProductRepository products;
    private final Mapper mapper;
    private final S3StorageService storage;
    private final OpenAiService openAi;

    public ProductService(ProductRepository products, Mapper mapper, S3StorageService storage, OpenAiService openAi) {
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
    public List<ProductResponse> listForVendor(UUID vendorId) {
        return products.findByVendorId(vendorId).stream().map(mapper::product).toList();
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
        if (req.currency() != null && !req.currency().isBlank()) p.setCurrency(req.currency());
        p.setQuantityAvailable(req.quantityAvailable());
        if (req.lowStockThreshold() != null) p.setLowStockThreshold(req.lowStockThreshold());
        p.setProductImageUrl(req.productImageUrl());
        p.setFulfillmentType(req.fulfillmentType());
        p.setOriginCountry(req.originCountry());
        if (req.availableNow() != null) p.setAvailableNow(req.availableNow());
        p.setBatchId(req.batchId());
        if (req.active() != null) p.setActive(req.active());
    }

    private Product ownedProduct(UUID vendorId, UUID productId) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!p.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This product belongs to another vendor");
        }
        return p;
    }
}
