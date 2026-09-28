package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String sku,
        BigDecimal price,
        Integer stockQuantity,
        boolean inStock,
        String imageUrl,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
