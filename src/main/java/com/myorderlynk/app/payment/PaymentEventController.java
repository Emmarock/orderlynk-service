package com.myorderlynk.app.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives domain events from the payment-service (PRD §13). Public route
 * ({@code /api/webhooks/**} is permitAll) — authenticity is established by HMAC
 * signature verification, not a JWT. Consumption is idempotent.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/payments")
@RequiredArgsConstructor
public class PaymentEventController {

    private final PaymentEventSignatureValidator signatureValidator;
    private final PaymentEventService paymentEventService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Payment-Signature", required = false) String signature) {
        if (!signatureValidator.isValid(signature, rawBody)) {
            log.warn("Rejected payment event with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        paymentEventService.apply(rawBody);
        return ResponseEntity.ok().build();
    }
}