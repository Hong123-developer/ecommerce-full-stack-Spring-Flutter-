package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Once a minute, releases card orders nobody paid: after the last attempt's lifetime
 * ({@code app.payway.lifetime-minutes}) plus a short grace, the payment becomes EXPIRED, the
 * order CANCELLED and its stock goes back. Each one is first re-checked with the provider,
 * so a payment that did go through is settled instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardPaymentExpiryJob {

    private final PaymentService paymentService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void releaseUnpaidCardOrders() {
        try {
            int released = paymentService.expireStaleCardPayments(LocalDateTime.now());
            if (released > 0) {
                log.info("Released {} unpaid card order(s) past their payment window", released);
            }
        } catch (RuntimeException ex) {
            log.warn("Card payment expiry sweep failed: {}", ex.getMessage());
        }
    }
}
