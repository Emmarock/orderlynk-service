package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.ProductDtos.ProductRequest;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.repo.ProductRepository;
import com.myorderlynk.app.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository products;
    private final Mapper mapper;

    public ProductService(ProductRepository products, Mapper mapper) {
        this.products = products;
        this.mapper = mapper;
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
