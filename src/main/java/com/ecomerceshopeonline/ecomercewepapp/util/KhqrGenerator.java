package com.ecomerceshopeonline.ecomercewepapp.util;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Builds a dynamic Bakong KHQR payload.
 * <p>
 * The format is EMVCo TLV: {@code tag + 2-digit length + value}, repeated, closed by a
 * CRC-16/CCITT-FALSE checksum computed over everything including the trailing {@code "6304"}.
 * <p>
 * Bakong identifies a payment by the <b>MD5 of the payload string</b>, which is what
 * {@code /v1/check_transaction_by_md5} expects.
 */
@Component
@RequiredArgsConstructor
public class KhqrGenerator {

    // --- EMVCo tags ---
    private static final String PAYLOAD_FORMAT = "00";
    private static final String INITIATION_METHOD = "01";
    private static final String MERCHANT_ACCOUNT = "29";
    private static final String MERCHANT_CATEGORY = "52";
    private static final String CURRENCY = "53";
    private static final String AMOUNT = "54";
    private static final String COUNTRY = "58";
    private static final String MERCHANT_NAME = "59";
    private static final String MERCHANT_CITY = "60";
    private static final String ADDITIONAL_DATA = "62";
    private static final String TIMESTAMP = "99";
    private static final String CRC = "63";

    /** "12" marks the QR dynamic: it carries an amount and is meant to be scanned once. */
    private static final String DYNAMIC = "12";
    private static final String COUNTRY_KH = "KH";
    private static final String MCC_GENERAL_RETAIL = "5999";
    private static final int MAX_MERCHANT_NAME = 25;
    private static final int MAX_MERCHANT_CITY = 15;
    private static final int MAX_BILL_NUMBER = 25;

    private final AppProperties properties;

    /** A generated QR: the string to render, the MD5 Bakong knows it by, and its lifetime. */
    public record Khqr(String payload, String md5, Instant createdAt, Instant expiresAt) {

        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public Khqr generate(BigDecimal amount, String currency, String billNumber) {
        AppProperties.Bakong config = properties.bakong();

        // Truncated to milliseconds because that is the precision the tag-99 timestamps carry;
        // keeping nanos would make the value read back out of the payload differ from this one.
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = createdAt.plusSeconds(config.expiryMinutes() * 60L);

        StringBuilder qr = new StringBuilder()
                .append(tlv(PAYLOAD_FORMAT, "01"))
                .append(tlv(INITIATION_METHOD, DYNAMIC))
                .append(tlv(MERCHANT_ACCOUNT, tlv("00", config.accountId())))
                .append(tlv(MERCHANT_CATEGORY, MCC_GENERAL_RETAIL))
                .append(tlv(CURRENCY, numericCurrency(currency)))
                .append(tlv(AMOUNT, formatAmount(amount, currency)))
                .append(tlv(COUNTRY, COUNTRY_KH))
                .append(tlv(MERCHANT_NAME, truncate(config.merchantName(), MAX_MERCHANT_NAME)))
                .append(tlv(MERCHANT_CITY, truncate(config.merchantCity(), MAX_MERCHANT_CITY)));

        if (billNumber != null && !billNumber.isBlank()) {
            qr.append(tlv(ADDITIONAL_DATA, tlv("01", truncate(billNumber, MAX_BILL_NUMBER))));
        }

        // Required for dynamic KHQR. Without it ACLEDA and other Bakong apps reject the code
        // with "Expiration timestamp is required for dynamic KHQR".
        qr.append(tlv(TIMESTAMP,
                tlv("00", String.valueOf(createdAt.toEpochMilli()))
                        + tlv("01", String.valueOf(expiresAt.toEpochMilli()))));

        qr.append(CRC).append("04");
        qr.append(crc16(qr.toString()));

        String payload = qr.toString();
        return new Khqr(payload, md5(payload), createdAt, expiresAt);
    }

    /** Reads the expiry back out of an already-issued payload, so it need not be stored twice. */
    public Optional<Instant> expiryOf(String payload) {
        return findTag(payload, TIMESTAMP)
                .flatMap(timestampBlock -> findTag(timestampBlock, "01"))
                .map(millis -> Instant.ofEpochMilli(Long.parseLong(millis)));
    }

    public String md5(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is required but unavailable", ex);
        }
    }

    // ------------------------------------------------------------------ TLV

    private String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    /** Walks the flat TLV sequence and returns the value of the first matching tag. */
    private Optional<String> findTag(String data, String wantedTag) {
        int cursor = 0;
        while (cursor + 4 <= data.length()) {
            String tag = data.substring(cursor, cursor + 2);
            int length;
            try {
                length = Integer.parseInt(data.substring(cursor + 2, cursor + 4));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
            int valueStart = cursor + 4;
            int valueEnd = valueStart + length;
            if (valueEnd > data.length()) {
                return Optional.empty();
            }
            if (tag.equals(wantedTag)) {
                return Optional.of(data.substring(valueStart, valueEnd));
            }
            cursor = valueEnd;
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ fields

    /** KHR is a whole-riel currency; USD carries two decimals. */
    private String formatAmount(BigDecimal amount, String currency) {
        return isKhr(currency)
                ? amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** ISO-4217 numeric codes - the QR carries the number, not the letters. */
    private String numericCurrency(String currency) {
        if (isKhr(currency)) {
            return "116";
        }
        if (currency == null || "USD".equalsIgnoreCase(currency)) {
            return "840";
        }
        throw new IllegalArgumentException("Unsupported KHQR currency: " + currency);
    }

    private boolean isKhr(String currency) {
        return "KHR".equalsIgnoreCase(currency);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** CRC-16/CCITT-FALSE: polynomial 0x1021, init 0xFFFF, no reflection. */
    public String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
