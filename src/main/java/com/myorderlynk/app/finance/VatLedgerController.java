package com.myorderlynk.app.finance;

import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.finance.VatDtos.VatLedgerEntryResponse;
import com.myorderlynk.app.finance.VatDtos.VatLedgerSummary;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.security.access.IsAdmin;
import com.myorderlynk.app.security.access.IsVendor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** VAT ledger reads: a vendor's own VAT to remit, and the platform's collected VAT (admin). */
@RestController
public class VatLedgerController {

    private final VatLedgerService vatLedger;
    private final CurrentUser currentUser;

    public VatLedgerController(VatLedgerService vatLedger, CurrentUser currentUser) {
        this.vatLedger = vatLedger;
        this.currentUser = currentUser;
    }

    /** VAT this vendor has collected and is responsible for remitting to the government. */
    @GetMapping("/api/vendor/vat")
    @IsVendor
    public VatLedgerSummary vendorVat(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is associated with your account");
        }
        Instant[] r = range(from, to);
        return vatLedger.forVendor(vendorId, r[0], r[1]);
    }

    /** Vendor marks one of its own VAT entries as remitted to the government. */
    @PostMapping("/api/vendor/vat/{id}/remit")
    @IsVendor
    public VatLedgerEntryResponse vendorRemit(@PathVariable UUID id) {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is associated with your account");
        }
        return vatLedger.markRemittedByVendor(id, vendorId);
    }

    /** VAT the platform has collected on vendors' behalf and must remit (collector = PLATFORM). */
    @GetMapping("/api/admin/vat")
    @IsAdmin
    public VatLedgerSummary platformVat(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return vatLedger.forPlatform(r[0], r[1]);
    }

    /** Admin marks a platform-collected VAT entry as remitted to the government. */
    @PostMapping("/api/admin/vat/{id}/remit")
    @IsAdmin
    public VatLedgerEntryResponse platformRemit(@PathVariable UUID id) {
        return vatLedger.markRemittedByPlatform(id);
    }

    /** Inclusive day range → instants; null bounds leave that side unbounded. */
    private static Instant[] range(LocalDate from, LocalDate to) {
        Instant start = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? null : to.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return new Instant[]{start, end};
    }
}
