package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Full order detail, including line items. */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discount,
        BigDecimal totalAmount,
        String shippingAddress,
        List<OrderItemResponse> items,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Long paymentId,
        /** True while the shopper is still allowed to cancel it themselves. */
        boolean cancellable,
        /** Present for admin views only. */
        CustomerSummary customer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record CustomerSummary(Long id, String fullName, String email, String phone) {
    }
}
