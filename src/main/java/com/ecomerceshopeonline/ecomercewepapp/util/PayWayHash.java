package com.ecomerceshopeonline.ecomercewepapp.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * ABA PayWay e-commerce checkout: endpoints, the purchase field order, the encodings and the
 * request hash - kept together so they can be compared against the docs in one place.
 * <p>
 * Docs: https://developer.payway.com.kh - "Ecommerce Checkout" (Purchase, Check Transaction,
 * Get Transaction Details). Hash = Base64(HMAC-SHA512(message, API key)), i.e. PHP's
 * {@code base64_encode(hash_hmac('sha512', $b4hash, $api_key, true))}.
 * <p>
 * Encodings (the hash covers the values exactly as they are posted):
 * <ul>
 *   <li>{@code req_time}: UTC, {@code yyyyMMddHHmmss}</li>
 *   <li>{@code amount}: USD with two decimals ("12.50"); KHR whole riel</li>
 *   <li>{@code items}: Base64 of a JSON array {@code [{"name","quantity","price"}]}</li>
 *   <li>{@code return_url}: Base64 of the callback URL</li>
 *   <li>{@code cancel_url}, {@code continue_success_url}: plain URLs, as the API reference
 *       documents them. Worth confirming in the sandbox: ABA's own WooCommerce plugin
 *       Base64-encodes {@code cancel_url} - if PayWay rejects or ignores the plain cancel URL,
 *       encode it with {@link #base64(String)} like {@code return_url}.</li>
 * </ul>
 * A field that is not posted contributes "" to the hash; blank fields are therefore simply
 * left out of the form.
 */
public final class PayWayHash {

    public static final String PURCHASE_PATH = "/api/payment-gateway/v1/payments/purchase";
    public static final String CHECK_TRANSACTION_PATH =
            "/api/payment-gateway/v1/payments/check-transaction-2";
    public static final String TRANSACTION_DETAIL_PATH =
            "/api/payment-gateway/v1/payments/transaction-detail";

    /**
     * Purchase hash order, exactly as the docs list it. {@code view_type} and
     * {@code payment_gate} are not part of the hash (and this integration does not send them).
     */
    public static final List<String> PURCHASE_HASH_FIELDS = List.of(
            "req_time", "merchant_id", "tran_id", "amount", "items", "shipping",
            "firstname", "lastname", "email", "phone", "type", "payment_option",
            "return_url", "cancel_url", "continue_success_url", "return_deeplink",
            "currency", "custom_fields", "return_params", "payout", "lifetime",
            "additional_params", "google_pay_token", "skip_success_page");

    private static final String HMAC = "HmacSHA512";
    private static final DateTimeFormatter REQ_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private PayWayHash() {
    }

    /** Hash of a purchase request: every hash field's value in doc order, missing ones as "". */
    public static String purchaseHash(Map<String, String> fields, String apiKey) {
        StringBuilder b4hash = new StringBuilder();
        for (String name : PURCHASE_HASH_FIELDS) {
            String value = fields.get(name);
            if (value != null) {
                b4hash.append(value);
            }
        }
        return sign(b4hash.toString(), apiKey);
    }

    /** Check Transaction and Get Transaction Details: req_time + merchant_id + tran_id. */
    public static String transactionHash(String reqTime, String merchantId, String tranId,
                                         String apiKey) {
        return sign(reqTime + merchantId + tranId, apiKey);
    }

    /** Base64(HMAC-SHA512(message, key)), UTF-8 on both sides. */
    public static String sign(String message, String apiKey) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            // HmacSHA512 is mandatory on every JVM; reaching this is a broken runtime.
            throw new IllegalStateException("HmacSHA512 is not available", ex);
        }
    }

    public static String reqTime(Instant instant) {
        return REQ_TIME.format(instant);
    }

    public static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * USD (and anything else) with two decimals, as in the docs' "0.10"; KHR has no
     * sub-unit, so whole riel (PayWay rejects KHR with decimals, error 46).
     */
    public static String formatAmount(BigDecimal amount, String currency) {
        int scale = "KHR".equalsIgnoreCase(currency) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}
