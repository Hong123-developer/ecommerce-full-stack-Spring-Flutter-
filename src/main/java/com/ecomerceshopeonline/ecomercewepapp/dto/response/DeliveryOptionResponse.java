package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One way to receive an order. The same shape serves the checkout and the admin. */
public record DeliveryOptionResponse(
        Long id,
        String name,
        String description,
        DeliveryType type,
        BigDecimal fee,
        String estimatedTime,
        String logoUrl,
        boolean enabled,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
