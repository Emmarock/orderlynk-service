package com.myorderlynk.app.catalog;

import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.common.enums.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByVendorId(UUID vendorId);

    List<Product> findByVendorIdAndActiveTrue(UUID vendorId);

    /** Ids of vendors that currently have at least one active product in the given category. */
    @Query("select distinct p.vendorId from Product p where p.active = true and p.category = :category")
    List<UUID> findVendorIdsByActiveCategory(ProductCategory category);
}
