package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        Long id,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String transactionId,
        /** Card provider, e.g. "ABA PayWay"; null for other methods (always serialised). */
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String gatewayProvider,
        /** Id of the latest card attempt at the provider (always serialised, may be null). */
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String gatewayTranId,
        /** Card approval code once paid (always serialised, may be null). */
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String gatewayApprovalCode,
        /** Brand and last four digits, e.g. "VISA •••• 4242" (always serialised, may be null). */
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String cardMasked,
        /** Present only when the shopper is actively paying by KHQR. */
        Khqr khqr,
        /** Present only on a card {@code pay} call that started a new attempt. */
        CardCheckout cardCheckout,
        LocalDateTime paidAt,
        OrderResponse.CustomerSummary customer,
        LocalDateTime createdAt
) {

    /**
     * The form the browser posts to the card provider's hosted payment page. Build a
     * {@code <form method="{method}" action="{action}">} with one hidden input per entry of
     * {@code fields} (names and values exactly as given, in this order) and submit it.
     * Card details are typed on the provider's page; they never pass through this API.
     * <p>
     * {@code display} says where that page goes: {@code "popup"} - post the form into a
     * secure popup over the shop (PayWay's own popup script for PayWay, otherwise an iframe
     * in a modal); {@code "redirect"} - submit it in the same tab.
     */
    public record CardCheckout(
            String action,
            String method,
            String display,
            Map<String, String> fields
    ) {
    }

    /**
     * Everything the frontend needs to show a scannable QR and poll for settlement.
     *
     * @param payload raw KHQR string, if you would rather render the QR yourself
     * @param md5     Bakong's identifier for this QR - also what the status poll uses
     * @param qrImage ready-to-display {@code data:image/png;base64,...}
     */
    public record Khqr(
            String payload,
            String md5,
            String qrImage,
            String bankName,
            String accountId,
            /** Absolute instant (serialised with a trailing Z) - unambiguous across time zones. */
            Instant expiresAt,
            /** Seconds left when this response was built; immune to client clock skew. */
            long expiresInSeconds
    ) {
    }
}
