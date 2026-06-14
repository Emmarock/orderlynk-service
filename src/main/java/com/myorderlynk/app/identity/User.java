package com.myorderlynk.app.identity;
import com.myorderlynk.app.common.BaseEntity;

import com.myorderlynk.app.common.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", indexes = @Index(name = "idx_user_email", columnList = "email", unique = true))
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Null for "invited" accounts created from a guest order — the customer sets it via the emailed
     * invite link, which also verifies their email. Required for password login.
     */
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private String city;

    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.CUSTOMER;

    /** Set for users whose role is VENDOR; points at the vendor they operate. */
    private UUID vendorId;

    @Column(nullable = false)
    private boolean admin = false;

    @Column(nullable = false)
    private boolean emailVerified = false;
}
