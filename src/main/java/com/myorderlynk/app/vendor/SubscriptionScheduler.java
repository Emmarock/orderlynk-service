package com.myorderlynk.app.vendor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * Generates monthly vendor subscription invoices. Runs on the 1st of each month (configurable via
 * {@code app.subscriptions.billing-cron}); generation is idempotent, so a missed/duplicate run is safe.
 */
@Component
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionBillingService billing;

    public SubscriptionScheduler(SubscriptionBillingService billing) {
        this.billing = billing;
    }

    /** 03:00 on the 1st of every month: bill the month that just started, then auto-collect. */
    @Scheduled(cron = "${app.subscriptions.billing-cron:0 0 3 1 * *}")
    public void runMonthlyBilling() {
        int generated = billing.generateMonthlyInvoices(YearMonth.now());
        int collected = collectDue();
        if (generated > 0 || collected > 0) {
            log.info("Monthly subscription billing: {} invoice(s) generated, {} collected", generated, collected);
        }
    }

    /**
     * Attempt to collect every still-DUE invoice (this month's plus any carried over). Each invoice is
     * collected in its own transaction so one failure never blocks the rest. Returns the count collected.
     */
    private int collectDue() {
        int collected = 0;
        for (java.util.UUID invoiceId : billing.dueInvoiceIds()) {
            if (billing.collectInvoice(invoiceId)) {
                collected++;
            }
        }
        return collected;
    }
}
