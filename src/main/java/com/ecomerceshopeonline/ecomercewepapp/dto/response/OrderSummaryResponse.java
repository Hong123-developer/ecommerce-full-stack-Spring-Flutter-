package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * List-view projection without line items. Fetching a collection alongside a Page
 * would force Hibernate to paginate in memory, so lists deliberately omit items.
 */
public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingAddress,
        /** Null on orders placed before delivery options existed. */
        DeliveryType deliveryType,
        String deliveryMethodName,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        boolean cancellable,
        OrderResponse.CustomerSummary customer,
        LocalDateTime createdAt
) {
}
