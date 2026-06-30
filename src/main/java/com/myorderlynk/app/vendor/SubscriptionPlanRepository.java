package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for the subscription plan catalog (one row per {@link VendorPlan}). */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByPlan(VendorPlan plan);

    List<SubscriptionPlan> findAllByOrderByMonthlyFeeAsc();
}
