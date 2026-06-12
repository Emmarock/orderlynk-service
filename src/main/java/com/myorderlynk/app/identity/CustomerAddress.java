package com.myorderlynk.app.identity;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A saved address in a customer's address book. A customer (user) may have many;
 * exactly one can be the default. Orders snapshot their own delivery address, so
 * editing/deleting here never changes historical orders.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_addresses", indexes = @Index(name = "idx_customer_address_user", columnList = "userId"))
public class CustomerAddress extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    /** Friendly label, e.g. "Home" or "Office". */
    private String label;

    @Embedded
    private Address address = new Address();

    @Column(nullable = false)
    private boolean isDefault = false;
}