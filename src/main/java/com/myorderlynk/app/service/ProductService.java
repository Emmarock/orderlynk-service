package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.ProductDtos.ProductRequest;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.repository.ProductRepository;
import com.myorderlynk.app.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    /** Image types we accept for product photos, mapped to their canonical file extension. */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final ProductRepository products;
    private final Mapper mapper;
    private final S3StorageService storage;

    public ProductService(ProductRepository products, Mapper mapper, S3StorageService storage) {
        this.products = products;
        this.mapper = mapper;
        this.storage = storage;
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
        String ext = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType.toLowerCase());
        if (ext == null) {
            throw ApiException.badRequest("Unsupported image type. Use JPEG, PNG, WebP or GIF.");
        }
        String key = "products/" + vendorId + "/" + UUID.randomUUID() + "." + ext;
        try {
            return storage.uploadPublic(file.getBytes(), contentType, key);
        } catch (IOException e) {
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
        return mapper.product(products.save(p));
    }

    @Transactional
    public ProductResponse update(UUID vendorId, UUID productId, ProductRequest req) {
        Product p = ownedProduct(vendorId, productId);
        apply(p, req);
        return mapper.product(products.save(p));
    }

    @Transactional
    public void delete(UUID vendorId, UUID productId) {
        Product p = ownedProduct(vendorId, productId);
        products.delete(p);
    }

    @Transactional
    public ProductResponse toggleActive(UUID vendorId, UUID productId, boolean active) {
        Product p = ownedProduct(vendorId, productId);
        p.setActive(active);
        return mapper.product(products.save(p));
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.category() != null) p.setCategory(req.category());
        p.setPrice(req.price());
        if (req.currency() != null && !req.currency().isBlank()) p.setCurrency(req.currency());
        p.setQuantityAvailable(req.quantityAvailable());
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
