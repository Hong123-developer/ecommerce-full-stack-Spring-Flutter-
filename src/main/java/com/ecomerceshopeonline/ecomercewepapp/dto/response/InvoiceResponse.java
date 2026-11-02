package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long orderId,
        String invoiceNumber,
        String orderNumber,
        LocalDateTime issuedAt,
        String shippingAddress,
        String contactName,
        String contactPhone,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discount,
        BigDecimal totalAmount,
        String couponCode,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        List<Line> items
) {
    public record Line(String productName, String variantLabel, String sku,
                       Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
