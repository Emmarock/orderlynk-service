package com.myorderlynk.app.order;

import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.common.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByPublicOrderId(String publicOrderId);

    boolean existsByPublicOrderId(String publicOrderId);

    List<Order> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<Order> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);

    List<Order> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);

    Page<Order> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId, Pageable pageable);

    List<Order> findByVendorIdAndCreatedAtBetween(UUID vendorId, Instant start, Instant end);

    List<Order> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID vendorId, Instant start, Instant end);

    Page<Order> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID vendorId, Instant start, Instant end, Pageable pageable);

    List<Order> findAllByOrderByCreatedAtDesc();

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** True if this customer has placed at least one order with the vendor (used to gate ratings). */
    boolean existsByCustomerUserIdAndVendorId(UUID customerUserId, UUID vendorId);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    /** Total gross revenue across orders in the given payment status (0 when none). */
    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.paymentStatus = :status")
    BigDecimal sumTotalAmountByPaymentStatus(PaymentStatus status);

    /** Total platform revenue across orders in the given payment status (0 when none). */
    @Query("select coalesce(sum(o.platformRevenue), 0) from Order o where o.paymentStatus = :status")
    BigDecimal sumPlatformRevenueByPaymentStatus(PaymentStatus status);
}
