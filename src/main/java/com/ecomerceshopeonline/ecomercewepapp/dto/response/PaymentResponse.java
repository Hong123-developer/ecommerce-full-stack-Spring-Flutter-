package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        /** Present only when the shopper is actively paying by KHQR. */
        Khqr khqr,
        LocalDateTime paidAt,
        OrderResponse.CustomerSummary customer,
        LocalDateTime createdAt
) {

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
            LocalDateTime expiresAt
    ) {
    }
}
