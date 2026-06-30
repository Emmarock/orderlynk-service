# OrderLynk Vendor Pricing & Monetization Model

> **Purpose:** Define the pricing model for vendors on OrderLynk so the platform generates
> revenue to cover infrastructure, engineering, and salaries, and reaches cash-flow positivity.
> Grounded in the monetization already implemented in the codebase.
>
> **Owner:** Finance / Founders · **Status:** Phases 1–3 implemented; settlement in progress · **Last updated:** 2026-06-30

> **Implementation status**
> - ✅ **Phase 1 (leak-plugging)** — shipped: logistics margin on (12%), processing gross-up + cross-border/FX buffer.
> - ✅ **Fee config moved to the DB** — fees are a single `fee_settings` row, admin-editable at runtime via `PUT /api/admin/fee-settings` (no redeploy). `app.fees.*` now only seeds a fresh environment. See §8.
> - ✅ **Phase 2 (vendor tiers / subscription)** — shipped: `Vendor.plan` (Starter/Growth/Pro), admin-editable plan catalog, monthly subscription invoices + scheduler. Existing vendors grandfathered. See §9.
> - ✅ **Phase 3 (value-added services)** — shipped: featured placement / promoted listings, instant-payout fee (1%), cargo handling fee (2%). All three knobs are admin-editable on `fee_settings`. See §10.
> - ✅ **Cross-service settlement / collection** — complete (4 increments). **Increment 1:** order + cargo payment allocations now route commission + service fee + logistics markup (+ cargo fee) to the platform (`PLATFORM_FEE` → `PLATFORM_REVENUE`); the vendor receives only their net. Fixed a real leak — orders previously never deducted the 7% commission. **Increment 2 (superseded by 3):** balance-netting collection. **Increment 3:** because the platform uses Stripe **destination charges** (the vendor's net settles straight to their own connected account) and **cannot legally hold vendor balances** (no money-transmitter licence), subscription + featured fees are collected by **charging the vendor's card on file** (off-session); the orphaned platform-payout transfer path was removed. **Increment 4:** the **instant-payout fee** now triggers a real **Stripe Instant Payout** on the vendor's connected account and charges the platform's fee to the card on file. See §11.

---

## 1. What OrderLynk already charges (live in code)

> Values below are the **current defaults**; all are now runtime-editable per §8 (the DB row is the source of truth, `app.fees.*` only seeds a fresh deploy).

| Lever | Current value | Who pays | Where |
|---|---|---|---|
| **Vendor commission** | **7%** of product subtotal (`Vendor.commissionRate = 0.07`), taken as a Stripe Connect destination application fee | Vendor (net of payout) | `Vendor.java:93`, `PaymentClient.java:99` |
| **Customer service fee** | **3%** of subtotal (`serviceFeeRate`), added on top at checkout | Customer | `FeeSettings` / `FeeCalculator` |
| **Payment processing** | 2.9% + **0.5% cross-border/FX buffer**, **grossed up** so the processor's cut on the grand total is recovered; passed through on card/Stripe only | Customer | `FeeSettings.processingFeeFor()` |
| **Logistics margin** | **12% markup on top** of the base carrier/flat cost (`logisticsMarginRate = 0.12`), so the carrier is always paid in full and the markup is platform margin | Customer | `FeeSettings.logisticsMarkupFor()`, `FeeCalculator` |

**Scope:** commission routes through the same Connect application-fee mechanism across **all**
GMV-generating modules — orders, service bookings (deposit + balance), batch orders, and cargo
shipments.

**Effective platform take now ≈ 12–13% of subtotal** (7% vendor commission + 3% customer service
fee + ~12% logistics markup on the shipping portion), with processing fully recovered rather than
leaking on cross-border. Pre-Phase-1 it was ~10% with processing under-recovering.

### Reference: current flat logistics fees
| Fulfillment type | Flat fee |
|---|---|
| Local pickup | $0.00 |
| Local delivery | $8.00 |
| Domestic shipping | $15.00 |
| Import batch | $25.00 |
| Export batch | $30.00 |

(Overridden by live Shippo rate at checkout when available — `FeeCalculator` `logisticsOverride`.)

---

## 2. Three structural revenue leaks (fix first)

1. ✅ **Logistics margin is zero.** *(Fixed in Phase 1.)* Was: Shippo labels resold at cost, 0%
   retained. Now: 12% markup added on top of the base carrier/flat cost, carrier paid in full.
2. ✅ **Processing under-recovery.** *(Fixed in Phase 1.)* Was: processing charged on the
   *pre-processing* base while Stripe bills on the *grand total*, plus ~1–2% uncovered cross-border/FX.
   Now: a 0.5% buffer is added and the fee is grossed up so the processor's cut is fully recovered.
3. ✅ **100% GMV-dependent, zero recurring revenue.** *(Fixed in Phase 2.)* Was: no subscription
   tier, so cash flow was fully GMV-dependent. Now: Starter/Growth/Pro tiers with monthly fees and a
   billing scheduler generate recurring MRR (still being rolled out — existing vendors are grandfathered).

---

## 3. Recommended model — hybrid "SaaS-enabled marketplace"

Keep transactional take, add recurring revenue, and let vendors self-select into paying more.

### Lever 1 — Tiered vendor commission + subscription (core change)
Replace the flat 7% with three tiers. High-GMV vendors pay a monthly fee to buy down commission.

| Tier | Monthly fee | Commission | Upgrade breakeven (vendor) | Best for |
|---|---|---|---|---|
| **Starter** | $0 | **10%** | — | New / low-volume vendors |
| **Growth** | $29/mo | **6%** | ~$725 GMV/mo | Steady sellers |
| **Pro** | $99/mo | **3.5%** | ~$1,520 GMV/mo | High-volume vendors |

Self-policing: a vendor upgrades only when volume makes it rational — at which point they convert
to predictable MRR. `commissionRate` is already a per-vendor field, so this is a `plan` column +
a scheduled MRR charge.

### Lever 2 — Keep the 3% customer service fee
Standard and tolerated (Airbnb, DoorDash, StubHub). Optionally band 2.5–4% by category.

### Lever 3 — Fix processing to net-neutral-plus
Gross-up so Stripe + Connect cross-border + FX are fully covered, with a ~0.3% buffer. Stops
cross-border from being a silent loss.

### Lever 4 — Turn on logistics margin
Set `logistics-margin-rate` to **10–12%**, or a flat **$2–3 label markup** per shipment on top of
the live Shippo rate. On cargo/batch, 12% is invisible to the buyer and ~100% margin.

### Lever 5 — Value-added services (high margin, optional)
- **Featured placement / search boost** — `ServiceDiscoveryService` already ranks listings; sell
  promoted slots (highest-margin line in mature marketplaces).
- **Instant payout fee** — 1% to settle before the standard Connect payout schedule.
- **Cargo handling/sourcing fee** — flat fee on high-touch import/export batches.

**Blended target take rate: ~12–13% of GMV** (up from ~10%), defensible band (Airbnb ~13%,
Etsy ~11% + ads), plus subscription MRR on top.

---

## 4. Unit economics (worked example)

Subtotal AOV = **$75**, Growth-tier vendor (6% commission):

```
Commission (6%)                     $4.50
Customer service fee (3%)           $2.25
Logistics margin (12% of $10 ship)  $1.20
Gross platform revenue              $7.95
Less processing leakage            −$0.25
Net contribution / order          ≈ $7.70   (≈10.3% of subtotal)
```

Net contribution per order ≈ **$7.50–$9.50** depending on tier mix (Starter higher per order; Pro
lower per order but carries $99 MRR).

---

## 5. Path to cash-flow positivity

**Break-even GMV = Monthly fixed cost ÷ blended take rate.** Subscription MRR lowers required GMV.

At a 12% blended take rate:

| Monthly fixed cost | GMV needed (no MRR) | Orders/mo @ $75 AOV | With $5k MRR, GMV needed |
|---|---|---|---|
| $12k (lean) | $100k | ~1,330 | $58k |
| $30k (base) | $250k | ~3,330 | $208k |
| $75k (scaled) | $625k | ~8,330 | $583k |

**Priority order of levers to reach CF+ faster:**
1. **Subscriptions** — every $5k MRR removes ~$42k/mo of GMV pressure and de-risks the forecast.
2. **Logistics margin on** — free ~1–2 points of take rate from infra already run.
3. **Processing leak fixed** — stops cross-border bleeding 1–2 points.
4. **Then** push GMV growth against a healthier take rate.

---

## 6. Rollout phases (implementation roadmap)

### Phase 1 — Leak-plugging ✅ shipped
- [x] Logistics margin on — **12% markup on top** of base carrier/flat cost (carrier paid in full, even on live Shippo rates). Optional flat per-shipment markup also available (`logisticsMarkupFlat`).
- [x] Gross-up processing fee so the processor's cut on the grand total is fully covered, plus a **0.5% cross-border/FX buffer** (`processingBufferRate`); toggle via `grossUpProcessing`.
- [x] Moved fee config to the DB (`fee_settings`) and exposed an admin API to edit it at runtime (see §8).
- **Impact:** +2–3 points of take rate; processing no longer leaks on cross-border. Locked by `FeeCalculatorTest`.

### Phase 2 — Recurring revenue ✅ shipped (see §9)
- [x] Added `plan` (enum: STARTER/GROWTH/PRO) to `Vendor`.
- [x] Plan → `commissionRate` via an **admin-editable catalog** (seeded 10% / 6% / 3.5%); assigning a plan materializes the rate onto the vendor.
- [x] Monthly subscription invoices ($0 / $29 / $99) via `SubscriptionScheduler` + `SubscriptionBillingService` (idempotent per vendor+month).
- [x] Existing vendors grandfathered onto Starter at their legacy 7% (no silent price change; new pricing applies only on explicit plan assignment).
- [ ] **Remaining:** auto-collect invoices via Stripe (today they generate `DUE` and are settled by `markPaid`). Needs a stored vendor payment method.
- **Impact:** recurring MRR mechanism in place; forecastable cash flow once vendors are migrated onto paid tiers.

### Phase 3 — High-margin upside ✅ shipped (see §10)
- [x] Featured placement / promoted listings in `ServiceDiscoveryService` — featured vendors rank first; paid slots ($25/7d, admin-editable) stack the window; ledger in `featured_placements`.
- [x] Instant payout fee (1%, admin-editable) — `PayoutService.requestInstantPayout` deducts the fee from net.
- [x] Cargo sourcing/handling fee (2%, admin-editable) — added on top of the shipment base charge in `ShipmentRequestService`.
- [ ] **Remaining:** route these fees + collect featured invoices via Stripe (the cross-service settlement item above).
- **Impact:** high-margin revenue lines (ad placement, finance product, cargo handling) wired and configurable.

---

## 7. Inputs needed to finalize the numbers

1. **Monthly fixed cost base** (infra + eng + salaries) — sets the break-even line.
2. **Primary market(s) / settlement currency** — cross-border (diaspora commerce?) changes
   processing economics materially.
3. **Current AOV and order volume** per module (orders vs. bookings vs. batch/cargo) — mix changes
   the blended take.
4. **Competitive ceiling** — what comparable platforms charge vendors, to stress-test the
   10% / 6% / 3.5% tiers for churn risk.

---

## 8. Admin-configurable fees (DB-backed) ✅ shipped

Fees are no longer a static config file — they live in a single `fee_settings` row that an admin
edits at runtime. Changes take effect immediately for new quotes and checkouts; no redeploy.

- **Source of truth:** the `fee_settings` DB row (+ `fee_settings_logistics` for per-fulfillment
  flat fees). `app.fees.*` in `application.yaml` only **seeds a fresh environment** on first boot
  (`FeeSettingsService.seedIfMissing` on `ApplicationReadyEvent`); editing the yaml does not change
  an already-seeded environment.
- **Read path:** `FeeCalculator` and `EarningsService` call `FeeSettingsService.current()`.
- **Admin API** (`@IsAdmin`, `AdminFeeSettingsController`):
  - `GET /api/admin/fee-settings` → current policy (+ `updatedAt`).
  - `PUT /api/admin/fee-settings` → replace policy. Body fields: `serviceFeeRate`, `processingRate`,
    `processingFixed`, `processingBufferRate`, `grossUpProcessing`, `logisticsMarginRate`,
    `logisticsMarkupFlat`, `taxRate`, `logistics` (map of `FulfillmentType` → flat fee). Rates are
    validated to `[0,1]`; money fields `@PositiveOrZero`.
- **Phase 2 hook:** per-vendor `commissionRate` stays on `Vendor`; this table holds the
  platform-wide knobs that apply across all vendors.

---

## 9. Vendor subscription tiers (Phase 2) ✅ shipped

Vendors sit on a tier (`Vendor.plan`: STARTER / GROWTH / PRO). Each tier maps — via an
admin-editable catalog — to a monthly fee and a commission rate. `commissionRate` remains the
**effective** rate used in all fee math; assigning a plan materializes the catalog's rate onto the
vendor (an admin can still set a bespoke rate for a custom deal).

- **Catalog (admin-editable):** `subscription_plans`, one row per tier, seeded on first boot with the
  proposed pricing — Starter $0/10%, Growth $29/6%, Pro $99/3.5% (`SubscriptionPlanService.seedIfMissing`).
- **Grandfathering:** the migration sets existing vendors to STARTER but leaves their legacy
  `commissionRate` (0.07) untouched; new vendors also default to STARTER at 0.07. The proposed tier
  pricing applies **only when an admin assigns a plan** — no silent price change.
- **Monthly billing:** `SubscriptionScheduler` (cron `app.subscriptions.billing-cron`, default 03:00
  on the 1st) calls `SubscriptionBillingService.generateMonthlyInvoices(YearMonth)`. One
  `vendor_subscription_invoices` row per (vendor, month); the unique constraint makes it idempotent.
  Starter (fee 0) and suspended vendors are skipped.
- **Collection:** invoices are created `DUE` and settled via `markPaid` (admin/manual) or `waive`.
  Auto-charge via Stripe is a future hook (needs a stored vendor payment method).
- **Admin API** (`@IsAdmin`, `AdminSubscriptionController`, `/api/admin/subscriptions`):
  - `GET /plans` · `PUT /plans/{plan}` — view / edit tier pricing.
  - `POST /vendors/{vendorId}/plan?plan=GROWTH` — assign a vendor to a tier (materializes the rate).
  - `POST /invoices/generate?period=YYYY-MM` — manual (idempotent) generation.
  - `GET /invoices?status=` · `GET /invoices/vendor/{vendorId}` — list.
  - `POST /invoices/{id}/mark-paid?reference=` · `POST /invoices/{id}/waive` — settle.

---

## 10. Value-added services (Phase 3) ✅ shipped

Three high-margin levers, all priced by admin-editable knobs on `fee_settings` (§8).

**Featured placement / promoted listings.** `Vendor.featuredUntil` marks a paid boost window;
`ServiceDiscoveryService.marketplace()` ranks featured providers first, then by rating
(`ProviderCard.featured` badges them). A purchase (`FeaturedPlacementService.purchase`) is priced from
`featuredPlacementFee`/`featuredPlacementDays` and **stacks** on any remaining window so paid time is
never lost; each purchase is a `featured_placements` ledger row (`DUE` → `markPaid`/`waive`).
- Vendor: `POST /api/vendor/featured/purchase`, `GET /api/vendor/featured`.
- Admin: `/api/admin/promotions/featured` (`AdminPromotionController`) — list, per-vendor, mark-paid, waive.

**Instant payout fee.** `PayoutService.requestInstantPayout` lets a vendor expedite a still-pending
payout for a fee (`instantPayoutFeeRate`, default 1%), deducted from the net and retained by the
platform; idempotent and ownership-checked. Recorded on `Payout.instantPayout`/`instantPayoutFee`.
- Vendor: `POST /api/vendor/payouts/{id}/instant`.

**Cargo sourcing/handling fee.** A platform fee (`cargoHandlingFeeRate`, default 2%) added on top of
a shipment's base charge (carrier/vendor portion paid in full). `ShipmentRequest.baseCharge()` is the
pre-fee charge; `computeCharge()` = base + `platformCargoFee`, set from settings at create and weigh.

**Settlement note:** the platform fees added in Phases 1–3 are recognized as platform revenue once a
payment is collected (see §11, Increment 1). Auto-*collecting* subscription + featured invoices is the
remaining cross-service item.

---

## 11. Cross-service settlement (in progress)

The `payment-service` is a double-entry ledger. On a Stripe Connect **destination charge** it sets
`applicationFee = gross − PRODUCT`, so the connected vendor receives only the `PRODUCT` allocation and
the platform retains everything else. The allocation buckets the backend sends map to ledger accounts:
`PRODUCT → VENDOR_PAYABLE`, `PLATFORM_FEE → PLATFORM_REVENUE`, `LOGISTICS → LOGISTICS_LIABILITY`,
`PROCESSING_FEE → PROCESSING_REVENUE`. **So the buckets are the settlement** — getting them right is
how fees actually reach the platform's balance and revenue.

### Increment 1 ✅ — order + cargo allocation routing (`PaymentClient`)

Audit finding: orders sent `PRODUCT = full productSubtotal`, so `applicationFee = gross − subtotal`
and **the 7% vendor commission was never deducted** (it existed only in reporting); the entire
logistics fee — including the Phase 1 markup — went to `LOGISTICS_LIABILITY` rather than being
recognized as revenue; and the Phase 3 cargo fee sat inside the vendor's portion.

Fix — make the buckets mirror the `FeeCalculator` economics already stored on the `Order`:
- `PRODUCT = vendorPayable` (subtotal − commission) — the vendor is paid only their net.
- `LOGISTICS = logisticsPayable` (carrier's actual cost only).
- `PLATFORM_FEE = serviceFee + commission + logisticsMarkup` (= `order.platformRevenue`, → `PLATFORM_REVENUE`).
- `PROCESSING_FEE` unchanged. Buckets sum exactly to the gross (payment-service validates this).
- **Cargo:** `createModulePayment` gained an `extraPlatformFee` overload; the `platformCargoFee` now
  routes to `PLATFORM_FEE` (proportional on partial payments) instead of the vendor's portion.

No payment-service change was needed — it already routes `PLATFORM_FEE → PLATFORM_REVENUE`. Locked by
`PaymentClientAllocationTest` (vendor gets net; platform gets commission + service fee + markup;
`PLATFORM_FEE == platformRevenue`; buckets sum to gross).

### Increment 2 — balance-netting collection (superseded by Increment 3)

The first collection attempt netted fees out of the vendor's internal `VendorBalance`
(`DEBIT VENDOR_PAYABLE / CREDIT PLATFORM_REVENUE`). The **collection wiring it added on the backend
is still in use** (scheduler generate-then-collect, featured collects on purchase, `collectInvoice` /
`dueInvoiceIds`, `PaymentClient.chargeVendor`). Only the payment-service *mechanism* behind
`/platform-charges` was replaced — see below for why.

### Increment 3 ✅ — card-on-file collection + closing the orphaned payout path

**Why the pivot.** The payment-service charges customers as Stripe **destination charges**
(`transfer_data.destination` + `application_fee_amount`): the vendor's net settles **directly to their
own connected Stripe account** at checkout; the platform keeps only the application fee. So the platform
**does not hold vendor funds** — and **holding/forwarding them would require a money-transmitter licence
the platform doesn't have**. Netting fees out of an internal balance therefore isn't real money. The
compliant mechanism is to **pull the fee from the vendor's card on file**.

- **Closed the orphaned payout path:** the payment-service also had a dormant "platform holds funds +
  Stripe `Transfer` payout" path (`POST /vendors/{id}/payouts`) — unused, but a latent **double-pay**
  (destination charge already paid the vendor). Removed the endpoint; `PayoutService.createPayout` now
  throws. (Verified nothing in the backend or any scheduler triggered it.)
- **Card-on-file billing (payment-service):** `PaymentProvider` gained a card-billing capability
  (Stripe `Customer` + `SetupIntent` + off-session `PaymentIntent`); `VendorBillingProfile`
  (customer + payment-method) persists it; `BillingService` ensures the customer, starts capture, and
  records the saved method (by retrieving the confirmed SetupIntent — no webhook needed).
- **`PlatformChargeService` rewritten:** charges the saved card off-session → `DEBIT CASH /
  CREDIT PLATFORM_REVENUE` + `RevenueType` recognition; **no card on file → fails**, so the invoice
  stays `DUE`; idempotent on reference. `/platform-charges` contract (and `chargeVendor`) unchanged, so
  the Increment-2 collection wiring now charges cards transparently.
- **Endpoints:** payment-service `POST /vendors/{id}/billing/setup-intent | /confirm`, `GET /billing`;
  backend `POST /api/vendor/billing/card | /card/confirm`, `GET /api/vendor/billing`.

Tests: `PlatformChargeServiceTest` (card path: charges card → books revenue; no card → fails;
non-success → fails; idempotent) and `SubscriptionCollectionTest` (collection wiring, `@MockitoBean`).

**Validation boundary:** the deterministic pieces (orchestration, ledger, persistence, endpoints,
idempotency) are tested with a mocked provider. The Stripe SDK calls are written per the SDK but not
exercised against live Stripe, and using it end-to-end needs (1) a **frontend Stripe Elements** card
form (the setup-intent endpoint returns the `clientSecret`), and optionally (2) a `setup_intent.succeeded`
webhook backstop (today the explicit `/confirm` call captures the card).

### Increment 4 ✅ — instant-payout fee (real Stripe Instant Payout + card fee)

The vendor's funds already sit in their connected account (destination charges), so an instant payout
moves **their own** balance to their bank and the platform charges a markup.

- **Payment-service:** `InstantPayoutService` + `POST /vendors/{id}/instant-payouts`. It charges the
  platform fee to the **card on file first** (`PlatformChargeService`, new `RevenueType.INSTANT_PAYOUT`)
  — so a vendor with no card is rejected before any money moves — then triggers a Stripe Instant
  `Payout` (`method=instant`, `RequestOptions.setStripeAccount(...)`) on the connected account
  (`PaymentProvider.createInstantPayout`). The vendor's bank receives the full amount; Stripe's own
  ~1.5% instant fee is borne by the connected account.
- **Backend:** `PaymentClient.requestInstantPayout`; `PayoutService.requestInstantPayout(vendorId,
  amount, currency)` computes the fee (`instantPayoutFeeRate`), drives the flow, and records a reporting
  `Payout` row (`INSTANT_<status>`). Vendor endpoint: `POST /api/vendor/payouts/instant?amount=&currency=`.

Tests: `InstantPayoutServiceTest` (fee-then-payout ordering, zero-fee skip, no-account / payouts-disabled
rejection, charge-failure aborts before payout); `Phase3ValueAddedTest` instant-payout cases reworked.

**Same Stripe validation boundary** as Increment 3. **Atomicity note:** the fee is charged before the
payout (fail-fast); if the payout fails *after* the fee is charged, the idempotent fee charge would need
a manual refund — refund-on-failure is a noted follow-up.

### Net result
Per-transaction fees are collected via the destination-charge application fee (Inc 1); every out-of-band
vendor charge — subscriptions, featured placement, instant-payout — is collected compliantly from the
card on file (Inc 3–4). No held vendor balances; no money-transmitter exposure.

---

## Appendix — Key code references

| Concern | File |
|---|---|
| Fee math (subtotal, logistics, platform, processing, payable, platform revenue) | `backend/src/main/java/com/myorderlynk/app/order/FeeCalculator.java` |
| **Live fee policy + fee helpers (DB-backed)** | `backend/src/main/java/com/myorderlynk/app/order/FeeSettings.java` |
| **Read / seed / update the policy** | `backend/src/main/java/com/myorderlynk/app/order/FeeSettingsService.java` |
| **Admin fee API** (`/api/admin/fee-settings`) | `backend/src/main/java/com/myorderlynk/app/order/AdminFeeSettingsController.java` |
| Bootstrap defaults only (`app.fees.*`) | `backend/src/main/java/com/myorderlynk/app/order/FeeProperties.java` + `application.yaml` |
| Fee schema migration | `backend/src/main/resources/db/changelog/changes/030-fee-settings.xml` |
| Per-vendor commission rate + `plan` | `backend/src/main/java/com/myorderlynk/app/vendor/Vendor.java` |
| Commission as Connect application fee (booking + module) | `backend/src/main/java/com/myorderlynk/app/payment/PaymentClient.java` |
| Booking deposit logic | `backend/src/main/java/com/myorderlynk/app/booking/ServiceOffering.java` (`depositFor`) |
| Earnings / commission rollup | `backend/src/main/java/com/myorderlynk/app/finance/EarningsService.java` |
| Fee math tests | `backend/src/test/java/com/myorderlynk/app/order/FeeCalculatorTest.java` |
| **Plan catalog (tier pricing)** | `backend/src/main/java/com/myorderlynk/app/vendor/SubscriptionPlan.java` + `SubscriptionPlanService.java` |
| **Plan assignment + monthly billing** | `backend/src/main/java/com/myorderlynk/app/vendor/SubscriptionBillingService.java` + `SubscriptionScheduler.java` |
| **Subscription invoices** | `backend/src/main/java/com/myorderlynk/app/vendor/VendorSubscriptionInvoice.java` |
| **Admin subscriptions API** (`/api/admin/subscriptions`) | `backend/src/main/java/com/myorderlynk/app/vendor/AdminSubscriptionController.java` |
| Subscription schema migration | `backend/src/main/resources/db/changelog/changes/031-vendor-plans.xml` |
| Subscription tests | `backend/src/test/java/com/myorderlynk/app/vendor/SubscriptionBillingServiceTest.java` |
| **Featured placement (ledger + purchase)** | `backend/src/main/java/com/myorderlynk/app/vendor/FeaturedPlacement.java` + `FeaturedPlacementService.java` |
| **Featured ranking boost** | `backend/src/main/java/com/myorderlynk/app/booking/ServiceDiscoveryService.java` (`marketplace`) |
| **Admin promotions API** (`/api/admin/promotions`) | `backend/src/main/java/com/myorderlynk/app/vendor/AdminPromotionController.java` |
| **Instant payout fee** | `backend/src/main/java/com/myorderlynk/app/finance/PayoutService.java` (`requestInstantPayout`) |
| **Cargo handling fee** | `backend/src/main/java/com/myorderlynk/app/batch/ShipmentRequest.java` (`baseCharge`/`computeCharge`) + `ShipmentRequestService.java` |
| Phase 3 schema migration | `backend/src/main/resources/db/changelog/changes/032-phase3-value-added.xml` |
| Phase 3 tests | `FeeSettingsPhase3Test.java` + `backend/src/test/java/com/myorderlynk/app/vendor/Phase3ValueAddedTest.java` |
| **Settlement: order/cargo allocation routing** | `backend/src/main/java/com/myorderlynk/app/payment/PaymentClient.java` (`orderAllocations`, `createModulePayment` extra-fee overload) |
| **Settlement: platform-charge + card-billing client** | `backend/.../payment/PaymentClient.java` (`chargeVendor`, `startCardSetup`/`confirmCard`/`billingStatus`) |
| **Settlement: invoice/featured collection** | `backend/.../vendor/SubscriptionBillingService.java` (`collectInvoice`/`dueInvoiceIds`), `SubscriptionScheduler.java`, `FeaturedPlacementService.java` |
| **Vendor card-billing endpoints (backend)** | `backend/.../vendor/VendorController.java` (`/api/vendor/billing/*`) |
| **Payment-service platform charge (card-on-file)** | `payment-service/.../service/PlatformChargeService.java` + `controller/PlatformChargeController.java` |
| **Payment-service card billing** | `payment-service/.../service/BillingService.java`, `controller/BillingController.java`, `domain/VendorBillingProfile.java`, `provider/stripe/StripePaymentProvider.java` (card-billing methods), migration `005-vendor-billing-profiles.sql` |
| **Closed payout path** | `payment-service/.../controller/VendorController.java` (payout endpoint removed), `service/PayoutService.java` (`createPayout` disabled) |
| **Instant payout (Stripe Instant Payout + card fee)** | `payment-service/.../service/InstantPayoutService.java` + `controller/InstantPayoutController.java` + `provider/stripe/StripePaymentProvider.java` (`createInstantPayout`); `backend/.../finance/PayoutService.java` (`requestInstantPayout`) |
| **Settlement tests** | `backend/.../payment/PaymentClientAllocationTest.java`, `backend/.../vendor/SubscriptionCollectionTest.java`, `backend/.../vendor/Phase3ValueAddedTest.java`, `payment-service/.../service/PlatformChargeServiceTest.java`, `payment-service/.../service/InstantPayoutServiceTest.java` |
| Payment-service ledger routing (allocation → account) | `payment-service/.../service/AllocationEngine.java`, `PaymentService.java` |