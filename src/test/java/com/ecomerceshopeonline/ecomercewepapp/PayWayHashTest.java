package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.util.PayWayHash;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PayWay request hash, field order and encodings against fixed vectors
 * (https://developer.payway.com.kh - Purchase / Check Transaction).
 */
class PayWayHashTest {

    private static final String KEY = "TEST_API_KEY";

    /** Base64(HMAC-SHA512) of the purchase example below, computed independently. */
    private static final String PURCHASE_HASH =
            "oiRaNEFNI0jdpYmDjfrlUq5gw8l3rivkfuoesluZpipo3p/VtT/lsIx0GQMmN42ORMICti5Ej43Lt+fiigFLjg==";

    @Test
    void hashOrderIsTheDocumentedOne() {
        assertThat(PayWayHash.PURCHASE_HASH_FIELDS).containsExactly(
                "req_time", "merchant_id", "tran_id", "amount", "items", "shipping",
                "firstname", "lastname", "email", "phone", "type", "payment_option",
                "return_url", "cancel_url", "continue_success_url", "return_deeplink",
                "currency", "custom_fields", "return_params", "payout", "lifetime",
                "additional_params", "google_pay_token", "skip_success_page");
    }

    @Test
    void purchaseHashFollowsTheDocOrderNotTheMapOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        // Deliberately scrambled: the hash must concatenate in the documented order.
        fields.put("return_params", "ORD1728289800123");
        fields.put("currency", "USD");
        fields.put("continue_success_url",
                "https://shop.example.com/checkout/success?order=ORD1728289800123");
        fields.put("return_url",
                "aHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20vYXBpL3BheW1lbnRzL3BheXdheS9jYWxsYmFjaw==");
        fields.put("payment_option", "cards");
        fields.put("type", "purchase");
        fields.put("phone", "012345678");
        fields.put("email", "sok@example.com");
        fields.put("lastname", "Dara");
        fields.put("firstname", "Sok");
        fields.put("items", "W3sibmFtZSI6IlQtc2hpcnQiLCJxdWFudGl0eSI6MSwicHJpY2UiOjEyLjV9XQ==");
        fields.put("amount", "12.50");
        fields.put("tran_id", "ORD1728289800123");
        fields.put("merchant_id", "ec000002");
        fields.put("req_time", "20261007083000");

        assertThat(PayWayHash.purchaseHash(fields, KEY)).isEqualTo(PURCHASE_HASH);
    }

    /** "A field you do not send contributes an empty string" - absent and blank are the same. */
    @Test
    void absentAndEmptyOptionalFieldsContributeNothing() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", "20261007083000");
        fields.put("merchant_id", "ec000002");
        fields.put("tran_id", "ORD1728289800123");
        fields.put("amount", "12.50");
        fields.put("items", "W3sibmFtZSI6IlQtc2hpcnQiLCJxdWFudGl0eSI6MSwicHJpY2UiOjEyLjV9XQ==");
        fields.put("shipping", "");
        fields.put("firstname", "Sok");
        fields.put("lastname", "Dara");
        fields.put("email", "sok@example.com");
        fields.put("phone", "012345678");
        fields.put("type", "purchase");
        fields.put("payment_option", "cards");
        fields.put("return_url",
                "aHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20vYXBpL3BheW1lbnRzL3BheXdheS9jYWxsYmFjaw==");
        fields.put("cancel_url", "");
        fields.put("continue_success_url",
                "https://shop.example.com/checkout/success?order=ORD1728289800123");
        fields.put("currency", "USD");
        fields.put("return_params", "ORD1728289800123");
        fields.put("lifetime", "");
        // Not part of the hash at all.
        fields.put("view_type", "hosted_view");
        fields.put("payment_gate", "0");

        assertThat(PayWayHash.purchaseHash(fields, KEY)).isEqualTo(PURCHASE_HASH);
    }

    @Test
    void transactionHashIsReqTimeMerchantTran() {
        assertThat(PayWayHash.transactionHash("20250213065545", "ec000002", "17394277693", KEY))
                .isEqualTo("Zr5NiGop7ifk9o91JeXvgYhn/uXRDQt7AC2YaSjjVsvpFH14kxBiHuPmzdyhO3rxH4LS57Q5fBCS6gSvAU5vCw==");
    }

    /** RFC 4231 test case 2, Base64 instead of hex. */
    @Test
    void signIsBase64OfHmacSha512() {
        assertThat(PayWayHash.sign("what do ya want for nothing?", "Jefe"))
                .isEqualTo("Fkt6e/z4GeLjlfvnO1bgo4e9ZCIugx/WECcM1+olBVSXWL91wFqZSm0DT2X48Ob9yuqxo01Ka0tjbgcKOLznNw==");
    }

    @Test
    void encodingsMatchTheDocs() {
        assertThat(PayWayHash.reqTime(Instant.parse("2026-10-07T08:30:00Z"))).isEqualTo("20261007083000");
        assertThat(PayWayHash.base64("https://api.example.com/api/payments/payway/callback"))
                .isEqualTo("aHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20vYXBpL3BheW1lbnRzL3BheXdheS9jYWxsYmFjaw==");
        assertThat(PayWayHash.formatAmount(new BigDecimal("12.5"), "USD")).isEqualTo("12.50");
        assertThat(PayWayHash.formatAmount(new BigDecimal("0.1"), "USD")).isEqualTo("0.10");
        assertThat(PayWayHash.formatAmount(new BigDecimal("51.505"), "USD")).isEqualTo("51.51");
        assertThat(PayWayHash.formatAmount(new BigDecimal("4000.00"), "KHR")).isEqualTo("4000");
        assertThat(List.of(PayWayHash.PURCHASE_PATH, PayWayHash.CHECK_TRANSACTION_PATH))
                .containsExactly("/api/payment-gateway/v1/payments/purchase",
                        "/api/payment-gateway/v1/payments/check-transaction-2");
    }
}
