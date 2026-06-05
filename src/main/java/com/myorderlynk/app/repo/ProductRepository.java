package com.myorderlynk.app.repo;

import com.myorderlynk.app.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByVendorId(UUID vendorId);

    List<Product> findByVendorIdAndActiveTrue(UUID vendorId);
}
