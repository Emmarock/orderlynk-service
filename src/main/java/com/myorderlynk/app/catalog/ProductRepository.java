package com.myorderlynk.app.catalog;

import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.common.enums.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByVendorId(UUID vendorId);

    Page<Product> findByVendorId(UUID vendorId, Pageable pageable);

    List<Product> findByVendorIdAndActiveTrue(UUID vendorId);

    /** Ids of vendors that currently have at least one active product in the given category. */
    @Query("select distinct p.vendorId from Product p where p.active = true and p.category = :category")
    List<UUID> findVendorIdsByActiveCategory(ProductCategory category);

    /** Ids of vendors that currently have at least one active product carrying the given tag. */
    @Query("select distinct p.vendorId from Product p join p.tags t where p.active = true and t = :tag")
    List<UUID> findVendorIdsByActiveTag(String tag);

    /** Distinct tags used across active products with their usage count, most-used first. */
    @Query("select t as tag, count(p) as uses from Product p join p.tags t "
            + "where p.active = true group by t order by count(p) desc, t asc")
    List<TagCount> findPopularTags(Pageable pageable);

    /** Projection for {@link #findPopularTags}. */
    interface TagCount {
        String getTag();
        long getUses();
    }

    /** A vendor's products at or below their (enabled) low-stock threshold, lowest stock first. */
    @Query("select p from Product p where p.vendorId = :vendorId and p.lowStockThreshold > 0 "
            + "and p.quantityAvailable <= p.lowStockThreshold order by p.quantityAvailable asc")
    List<Product> findLowStockByVendor(UUID vendorId);
}
