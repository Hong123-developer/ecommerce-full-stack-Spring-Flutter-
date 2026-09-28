package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.util.KhqrGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KhqrGeneratorTest {

    private static final AppProperties.Bakong CONFIG = new AppProperties.Bakong(
            "https://api-bakong.nbc.gov.kh", "token",
            "prol_menghong@bkrt", "MENGHONG PROL", "Phnom Penh", "USD", 30);

    private final KhqrGenerator generator = new KhqrGenerator(
            new AppProperties(null, null, null, null, null, null, null, CONFIG));

    /**
     * The canonical CRC-16/CCITT-FALSE check value: "123456789" must hash to 0x29B1.
     * If this drifts, every QR produced would be rejected by any scanner.
     */
    @Test
    void crc16MatchesTheStandardCheckValue() {
        assertThat(generator.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void payloadCarriesTheExpectedEmvTags() {
        String payload = generator.generate(
                new BigDecimal("51.50"), "USD", "ORD-20260927-ABC123").payload();

        assertThat(payload).startsWith("000201010212");
        assertThat(payload).contains("00" + "18" + "prol_menghong@bkrt");
        assertThat(payload).contains("5303840");
        assertThat(payload).contains("540551.50");
        assertThat(payload).contains("5802KH");
        assertThat(payload).contains("5913MENGHONG PROL");
    }

    /** Dynamic KHQR is rejected by ACLEDA and others without the tag-99 timestamps. */
    @Test
    void dynamicQrIncludesCreationAndExpiryTimestamps() {
        KhqrGenerator.Khqr khqr = generator.generate(BigDecimal.ONE, "USD", "ORD-1");

        // tag 99, length 34 = two 13-digit timestamps each wrapped in a 4-char TLV header
        assertThat(khqr.payload()).contains("9934");
        assertThat(khqr.expiresAt()).isEqualTo(khqr.createdAt().plusSeconds(30 * 60));
        assertThat(generator.expiryOf(khqr.payload())).contains(khqr.expiresAt());
    }

    @Test
    void expiryCanBeReadBackOutOfAnIssuedPayload() {
        KhqrGenerator.Khqr khqr = generator.generate(BigDecimal.TEN, "USD", "ORD-2");

        assertThat(khqr.isExpired(Instant.now())).isFalse();
        assertThat(khqr.isExpired(khqr.expiresAt().plusSeconds(1))).isTrue();
    }

    @Test
    void crcIsAppendedAndCoversTheWholePayload() {
        String payload = generator.generate(new BigDecimal("1.00"), "USD", null).payload();

        String body = payload.substring(0, payload.length() - 4);
        String checksum = payload.substring(payload.length() - 4);

        assertThat(body).endsWith("6304");
        assertThat(checksum).matches("[0-9A-F]{4}");
        assertThat(generator.crc16(body)).isEqualTo(checksum);
    }

    /** Riel has no sub-unit, so the amount must not carry decimals. */
    @Test
    void khrAmountIsWholeRiel() {
        String payload = generator.generate(new BigDecimal("4000.00"), "KHR", null).payload();

        assertThat(payload).contains("5303116");
        assertThat(payload).contains("54044000");
        assertThat(payload).doesNotContain("4000.00");
    }

    @Test
    void md5IsStableForTheSamePayload() {
        String payload = generator.generate(BigDecimal.ONE, "USD", "ORD-3").payload();

        assertThat(generator.md5(payload))
                .hasSize(32)
                .isEqualTo(generator.md5(payload));
    }

    @Test
    void unsupportedCurrencyIsRejected() {
        assertThatThrownBy(() -> generator.generate(BigDecimal.ONE, "EUR", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EUR");
    }
}
