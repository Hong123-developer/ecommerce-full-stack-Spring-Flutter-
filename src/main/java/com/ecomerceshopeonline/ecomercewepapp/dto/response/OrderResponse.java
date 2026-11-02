package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full order detail, including line items.
 * <p>
 * The delivery and contact fields are null on orders placed before delivery options
 * existed (those were all deliveries to {@code shippingAddress}).
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal subtotal,
        /** The chosen option's fee at checkout time. */
        BigDecimal deliveryFee,
        BigDecimal discount,
        String couponCode,
        BigDecimal totalAmount,
        /** Delivery address snapshot, or "Pick up at store" (plus the store address) for pickup. */
        String shippingAddress,
        Long deliveryOptionId,
        /** Snapshot of the option's name, e.g. "J&T Express". */
        String deliveryMethodName,
        DeliveryType deliveryType,
        String contactName,
        String contactPhone,
        /** The shopper's note for the order. */
        String note,
        /** Map pin of the delivery address at checkout; null for pickup. */
        Double latitude,
        Double longitude,
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
