package com.myorderlynk.app.vendor;

import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.common.enums.VendorPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The subscription plan catalog: per-tier monthly fee and commission rate. Seeded once with the
 * proposed pricing (Starter $0/10%, Growth $29/6%, Pro $99/3.5%) and edited by an admin thereafter.
 */
@Service
public class SubscriptionPlanService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanRepository repo;

    public SubscriptionPlanService(SubscriptionPlanRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> all() {
        return repo.findAllByOrderByMonthlyFeeAsc();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan byPlan(VendorPlan plan) {
        return repo.findByPlan(plan)
                .orElseThrow(() -> ApiException.notFound("Subscription plan " + plan + " is not configured"));
    }

    /** Update one tier's pricing. */
    @Transactional
    public SubscriptionPlan update(VendorPlan plan, String displayName, BigDecimal monthlyFee,
                                   BigDecimal commissionRate, String currency) {
        SubscriptionPlan p = byPlan(plan);
        p.setDisplayName(displayName);
        p.setMonthlyFee(monthlyFee);
        p.setCommissionRate(commissionRate);
        p.setCurrency(currency);
        SubscriptionPlan saved = repo.save(p);
        log.info("Plan {} updated: fee={} {} commission={}", plan, saved.getMonthlyFee(),
                saved.getCurrency(), saved.getCommissionRate());
        return saved;
    }

    /** Seed the catalog from the proposed defaults on first boot. No-op once populated. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfMissing() {
        if (repo.count() > 0) {
            return;
        }
        repo.save(new SubscriptionPlan(VendorPlan.STARTER, "Starter",
                new BigDecimal("0.00"), new BigDecimal("0.10"), "CAD"));
        repo.save(new SubscriptionPlan(VendorPlan.GROWTH, "Growth",
                new BigDecimal("29.00"), new BigDecimal("0.06"), "CAD"));
        repo.save(new SubscriptionPlan(VendorPlan.PRO, "Pro",
                new BigDecimal("99.00"), new BigDecimal("0.035"), "CAD"));
        log.info("Seeded subscription plan catalog (Starter/Growth/Pro)");
    }
}
