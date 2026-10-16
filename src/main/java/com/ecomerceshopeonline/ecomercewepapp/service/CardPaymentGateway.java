package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A card provider with a hosted payment page: the shopper's browser posts a signed form to
 * the provider, types the card there, and this server only ever learns the outcome. Card
 * numbers, CVV and expiry dates never reach us.
 * <p>
 * Provider-neutral on purpose - {@code PayWayCardGateway} is the ABA PayWay implementation.
 */
public interface CardPaymentGateway {

    /** Display name, also stored on the payment, e.g. "ABA PayWay". At most 20 characters. */
    String provider();

    /** False while credentials are missing - card is then hidden and refused. */
    boolean isConfigured();

    /**
     * How long one attempt stays payable at the provider. An unpaid card order is released
     * (EXPIRED, stock back) once its latest attempt is this old, plus a short grace period.
     */
    default int attemptLifetimeMinutes() {
        return 30;
    }

    /**
     * Signs a new payment attempt for the order's total.
     *
     * @param tranId unique id of this attempt (a retry always gets a new one)
     */
    CardCheckout createCheckout(Order order, Payment payment, String tranId);

    /**
     * Asks the provider what happened to an attempt. This answer - never a callback body -
     * is what a payment is settled on.
     *
     * @throws com.ecomerceshopeonline.ecomercewepapp.exception.ApiException 503 when the
     *         provider cannot be reached
     */
    CardTransactionResult check(String tranId);

    /**
     * What the browser posts to the hosted page.
     *
     * @param action  absolute URL of the provider's purchase endpoint
     * @param method  always {@code POST}
     * @param display how the shop shows the provider's page: {@link #DISPLAY_POPUP} (a secure
     *                popup over the shop, the form posted into it) or {@link #DISPLAY_REDIRECT}
     *                (the form submitted in the same tab)
     * @param fields  form field name -> value, in the order they must be posted (incl. hash)
     */
    record CardCheckout(String action, String method, String display, Map<String, String> fields) {

        public static final String DISPLAY_POPUP = "popup";
        public static final String DISPLAY_REDIRECT = "redirect";
    }

    /**
     * The provider's verdict on one attempt.
     *
     * @param amount       what the provider says was charged (null when unknown)
     * @param currency     ISO code of that amount
     * @param approvalCode the bank's approval code, once paid
     * @param cardMasked   brand and last four digits only, when the provider tells us
     * @param rawStatus    the provider's own status text, for logs and support
     */
    record CardTransactionResult(
            Status status,
            BigDecimal amount,
            String currency,
            String approvalCode,
            String cardMasked,
            String rawStatus
    ) {
        public enum Status {
            /** Approved - money taken. */
            PAID,
            /** Started but not finished (or the provider could not say right now). */
            PENDING,
            /** Declined, cancelled or reversed - the shopper may try again. */
            FAILED,
            /** The provider has never seen this attempt (e.g. the form was never submitted). */
            NOT_FOUND
        }

        public static CardTransactionResult of(Status status, String rawStatus) {
            return new CardTransactionResult(status, null, null, null, null, rawStatus);
        }
    }
}
