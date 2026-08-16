# Vendor Agreement E-Signature — OpenSign Integration

Design note for signing the OrderLynk vendor terms of service with
[OpenSign](https://www.opensignlabs.com/). Documentation only — nothing here is implemented yet.

Last researched: 2026-08-14 (OpenSign API v1.2, OSS repo `main`).

---

## 1. Where we are today

There is **no terms-of-service concept anywhere in the platform** — no acceptance flag, no stored
document, no audit record. This is greenfield.

The natural place to attach it is vendor onboarding, which already runs a two-gate approval:

| Gate | Where |
| --- | --- |
| Owner email verified | `User.isEmailVerified()` |
| WhatsApp number verified | `Vendor.whatsappVerified` — `vendor/Vendor.java:67` |
| **Both enforced at approval** | `admin/AdminService.java:86` (`approveVendor`) |

`VendorStatus` is `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED → REJECTED/SUSPENDED`
(`common/enums/VendorStatus.java`). A signed agreement slots in as a **third gate on the same
method**: an admin cannot approve until the vendor agreement is `COMPLETED`.

Service providers are vendors too (`ServiceProviderProfile` is keyed by `vendorId`), so one signed
agreement per `Vendor` covers marketplace, booking, and batch/cargo — no second signer entity.

---

## 2. The cost reality — read this before anything else

**Both viable paths cost money.** OpenSign is AGPL-3.0 and self-hostable for free, but:

| Path | API access | Cost |
| --- | --- | --- |
| Cloud — Free plan | **Sandbox token only** | £0, but sandbox-only (5 MB doc cap, not for real data) |
| Cloud — Professional | Live `x-api-token` | ~$29.99/mo, 100 API signatures included |
| Cloud — Teams | Live `x-api-token` | ~$39.99/user/mo, 500 API signatures included |
| **Self-host — free (OSS)** | **None. No API token generation at all.** | £0 |
| Self-host — paid | Live token | Quote-based; must upgrade the self-host plan |

The free self-hosted build genuinely cannot issue API tokens — confirmed both in the docs
("The free self-hosted version does not support API token generation") and by inspecting the OSS
repo: `apps/OpenSignServer/index.js` mounts only Parse Server at `/app` plus a handful of custom
routes in `cloud/customRoute/customApp.js` (`docxtopdf`, `decryptpdf`, delete-account). **There is
no `/api/v1*` router in the open-source tree**, and `x-api-token` appears nowhere in it. A
long-standing community question about this ([discussion #1500](https://github.com/OpenSignLabs/OpenSign/discussions/1500))
is still unanswered.

So "self-host it for free and drive it over the API" is not an option. The free self-host is
UI-only — a human logs in and sends documents by hand.

### Self-hosting also means MongoDB

`docker-compose.yml` runs four containers: `opensign/opensignserver`, `opensign/opensign` (client),
`mongo:latest`, and `caddy`. OpenSign is a Parse Server app — **MongoDB is mandatory**, plus S3-
compatible object storage and an SMTP/Mailgun sender:

```
APP_ID / MASTER_KEY          # 12-char app id + master key
MONGODB_URI                  # mongodb://user:pass@host:27017
PARSE_MOUNT=/app
SERVER_URL / PUBLIC_URL
DO_SPACE / DO_ENDPOINT / DO_BASEURL / DO_ACCESS_KEY_ID / DO_SECRET_ACCESS_KEY / DO_REGION
                             # or USE_LOCAL=TRUE for local disk
MAILGUN_API_KEY / MAILGUN_DOMAIN / MAILGUN_SENDER   # or SMTP_ENABLE + SMTP_*
PFX_BASE64 / PASS_PHRASE     # signing certificate
```

Both OrderLynk services are PostgreSQL on Render (see the deployment notes). Self-hosting OpenSign
introduces a brand-new datastore, a fourth deployable, an object-storage bucket, and a signing
certificate to rotate — for a document every vendor signs exactly once.

### Recommendation

**Start on OpenSign Cloud Professional.** Build and test against the sandbox token (free), then flip
one env var to the live token when going to production. Revisit self-hosting only if signature
volume makes the per-signature cost hurt, at which point the paid self-host plan — not the free one
— is the actual comparison.

> Worth stating plainly since it changes the cost/benefit: a platform vendor agreement is normally
> enforceable as **click-wrap** (timestamped "I agree" + versioned terms + IP/user-agent audit
> record), which needs no third party at all. E-signature buys stronger evidentiary weight and a
> countersigned PDF artifact. If that is what you want, OpenSign is a reasonable choice — the design
> below assumes it. The click-wrap fallback is sketched in §8.

---

## 3. API surface we would use

**Base URLs** (version is in the path):

- Sandbox — `https://sandbox.opensignlabs.com/api/v1.2`
- Production — `https://app.opensignlabs.com/api/v1.2`
- EU region — `https://eu-app.opensignlabs.com/api/v1.2`

**Auth** — every request carries `x-api-token: <token>`. Sandbox and live tokens are not
interchangeable, and **templates do not cross environments** — the ToS template must be created
twice, once per environment, and the id stored per environment.

v1 and v1.1 endpoints remain served on the v1.2 base URL, so pin `v1.2`.

**The endpoints this integration needs:**

| Purpose | Method + path |
| --- | --- |
| Create the vendor's agreement from our ToS template | `POST /createdocument/:template_id` |
| Fetch per-signer signing URLs (to embed/link) | `GET /signinglinks/:document_id` |
| Poll document state (reconciliation safety net) | `GET /document/:document_id` |
| Register our webhook endpoint | `POST /webhook` |
| Re-send the signing email | `POST /resendrequestmail` |
| Revoke a superseded agreement | `POST /revokedocument` |
| Remaining credit balance (billing alerting) | `GET /getcredits` |

Full list: `https://docs.opensignlabs.com/docs/API-docs/v1.2/`.

### Create-from-template body

The template carries the PDF, the widget layout, and the signer roles; the API call supplies the
people and any prefilled values. Widgets are addressed **by name**, and defaults are supported for
every widget type except signature, stamp, initials, and image:

```jsonc
{
  "signers": [
    { "role": "vendor", "name": "Ada Vendor", "email": "ada@example.com" }
  ],
  "prefill_widgets": [
    { "name": "textbox", "response": "Ada's Kitchen Ltd" },
    { "name": "date",    "response": "08/14/2026" }
  ]
}
```

Responses: `200` created · `400` template has no widgets/signers, or signers were sent without
roles · `405` invalid API token.

> **Verify before coding.** The exact top-level field names (title, note, `send_email`,
> `redirect_url`, `sendInOrder`, folder id, expiry-in-days) are rendered client-side in the docs and
> could not be extracted statically. Create the template in the sandbox Debug UI, fire one real
> `POST /createdocument/:template_id`, and pin the schema from the live response before writing the
> client. The same applies to the `signinglinks` response shape.

---

## 4. Webhook contract

OpenSign posts five document lifecycle events to a URL registered via `POST /webhook`:

`Document Created` · `Document Viewed` · `Document Signed` · `Document Completed` ·
`Document Declined/Revoked`

Payload carries `event`, `type: "request-sign"`, `objectId` (document id), `file` (document URL),
`name`/`note`/`description`, a `signers` array, and event-specific extras (`viewedBy`, `signedAt`,
`declinedReason`).

**Authentication is HMAC-SHA256 over the raw JSON body, in the `x-webhook-signature` header**, keyed
by the webhook security key from OpenSign settings.

This is the same shape as our existing payment-service events, so reuse the pattern verbatim:
`payment/PaymentEventSignatureValidator.java` — lowercase-hex HMAC-SHA256 of the raw body compared
with `MessageDigest.isEqual`. Note it compares hex *lowercase*; confirm OpenSign's encoding (hex vs
base64, case) against a real sandbox delivery before trusting it.

`POST /api/webhooks/**` is already permit-all in `security/SecurityConfig.java:82`, so a new
controller at `/api/webhooks/opensign` needs no security change — but it **must** verify the
signature itself, since the path is unauthenticated.

---

## 5. Proposed design in the backend

Package-by-feature, so a new `app/agreement/` package alongside `vendor/`.

**Entity — `VendorAgreement`** (one row per vendor per terms version; keep history, never mutate a
completed row):

| Field | Notes |
| --- | --- |
| `vendorId` | FK to `Vendor` |
| `termsVersion` | e.g. `2026-08` — lets us re-paper everyone when terms change |
| `provider` | `OPENSIGN` — leaves room to swap providers |
| `providerDocumentId` | OpenSign `objectId` |
| `status` | `PENDING → SENT → VIEWED → COMPLETED`, plus `DECLINED`, `REVOKED`, `EXPIRED` |
| `signerEmail` | snapshot of who it went to (owner email at send time) |
| `signedAt`, `completedAt`, `declinedAt`, `declineReason` | audit trail |
| `signedPdfUrl` | OpenSign `file` URL from the completed event |
| `signedPdfKey` | our S3 copy — see §7 |

**Service — `VendorAgreementService`**

- `sendAgreement(vendorId)` — idempotent: reuse any non-terminal agreement for the current
  `termsVersion` rather than burning a second signature credit.
- `signingLink(vendorId)` — `GET /signinglinks/:document_id`, returned to the vendor UI.
- `applyWebhook(event, payload)` — advance status; on `Document Completed`, archive the PDF and
  stamp `completedAt`.
- `hasCurrentAgreement(vendorId)` — the predicate the approval gate calls.

**Client — `OpenSignClient`** with `OpenSignProperties` bound from `opensign.*`, mirroring
`shipping/ShippingProperties.java`: blank token ⇒ feature disabled and calls fail with a clean
error, rather than a half-configured environment booting into breakage.

**Controllers**

- `VendorAgreementController` — `GET /api/vendor/agreement` (status + signing link),
  `POST /api/vendor/agreement/send` (issue or re-send).
- `OpenSignWebhookController` — `POST /api/webhooks/opensign`, `@RequestBody String` raw for HMAC,
  returns 200 for well-formed posts so OpenSign does not retry on our mapping quirks (exactly how
  `ShippoWebhookController` behaves).
- `AdminService.approveVendor` — add the third gate next to the email/WhatsApp checks at
  `admin/AdminService.java:86`, extending the same "surface exactly what's outstanding" error
  message so approval never fails silently.

**Liquibase** — one new changeset for `vendor_agreements`, XML with abstract types, `VARCHAR(36)` PK
+ `@JdbcTypeCode(SqlTypes.CHAR)`, matching the existing changelog conventions.

**Migration for existing vendors** — every currently-approved vendor predates this. Do **not**
retroactively suspend them: gate only *new* approvals, then run a back-fill campaign that sends
agreements to live vendors with a deadline. This mirrors how existing users were grandfathered
through the email-verification rollout.

---

## 6. Proposed design in the frontend

`VendorVerificationCard.tsx` already renders the outstanding email/WhatsApp gates on the vendor
dashboard — the agreement becomes a third row in that card:

- **Not sent** → "Sign your vendor agreement" + button calling `POST /api/vendor/agreement/send`.
- **Sent/viewed** → status pill + "Open signing page" (the OpenSign signing URL) + "Resend email".
- **Completed** → green check + link to the countersigned PDF.

`AdminVendors.tsx` shows the same status so an admin sees why approval is blocked. If OpenSign
supports a post-signing `redirect_url`, point it at a small `/vendor/agreement/return` route that
refetches status, following the `VendorOnboardingReturn.tsx` pattern already used for Stripe.

---

## 7. Configuration and secrets

New `opensign.*` properties, wired through `render.yaml` as `sync: false` vars on the backend
service (the convention every third-party credential already follows):

| Env var | Value |
| --- | --- |
| `OPENSIGN_BASE_URL` | `https://sandbox.opensignlabs.com/api/v1.2` in staging, `https://app…` in prod |
| `OPENSIGN_API_TOKEN` | sandbox token on `dev`, live token on `main` |
| `OPENSIGN_TEMPLATE_ID` | **different per environment** — templates don't cross |
| `OPENSIGN_WEBHOOK_SECRET` | webhook security key for HMAC verification |
| `OPENSIGN_TERMS_VERSION` | e.g. `2026-08` |

Since `dev → staging` and `main → production`, staging naturally runs the free sandbox token and
only production consumes paid signature credits.

**Archive the signed PDF ourselves.** The `file` URL in the webhook points at OpenSign's storage;
copy it into our existing S3 bucket on completion so the agreement survives a lapsed subscription or
a provider switch. This is the single most important durability decision in the integration.

---

## 8. Open items

1. **Pin the request/response schemas** against the sandbox before writing `OpenSignClient` — the
   docs render them client-side (§3).
2. **Confirm the HMAC encoding** (hex vs base64, case, and whether the header includes a timestamp)
   from a real sandbox delivery (§4).
3. **Signature-credit budget** — 100/mo on Professional. At more than ~3 new vendors a day the Teams
   plan or extra credits become the cheaper line item; `GET /getcredits` should feed an alert.
4. **Who countersigns?** If OrderLynk must sign too, the template needs a second role and the flow
   completes only after our signature — decide before building the template.
5. **Terms re-papering** — when `OPENSIGN_TERMS_VERSION` changes, do live vendors get suspended, or
   nagged with a deadline? Recommend nagged.
6. **Click-wrap fallback** — if the per-signature cost is not worth it, the same
   `VendorAgreement` entity works with `provider = CLICKWRAP`: store terms version, accepted-at, IP,
   and user-agent, drop the OpenSign client and webhook, and keep the identical approval gate. The
   design above is deliberately provider-agnostic so this stays a one-package change.

---

## Sources

- [OpenSign API v1.2 overview](https://docs.opensignlabs.com/docs/API-docs/v1.2/intro/)
- [Create Document from Template](https://docs.opensignlabs.com/docs/API-docs/v1.2/createdocumentwithtemplateid)
- [Get Signing Links](https://docs.opensignlabs.com/docs/API-docs/v1.2/getsigninglinks)
- [Save/Update Webhook](https://docs.opensignlabs.com/docs/API-docs/v1.2/save-update-webhook)
- [Webhook events & HMAC verification](https://docs.opensignlabs.com/docs/help/Settings/Webhook/)
- [API Token — sandbox vs live, self-host limits](https://docs.opensignlabs.com/docs/help/Settings/APIToken/)
- [OpenSign self-hosting docs](https://docs.opensignlabs.com/docs/self-host/intro/)
- [OpenSign source (AGPL-3.0)](https://github.com/OpenSignLabs/OpenSign) — `docker-compose.yml`,
  `.env.example`, `apps/OpenSignServer/index.js`, `cloud/customRoute/customApp.js`
- [Discussion #1500 — API on self-host?](https://github.com/OpenSignLabs/OpenSign/discussions/1500)