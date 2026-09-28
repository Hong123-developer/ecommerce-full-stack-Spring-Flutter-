package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        /** False when the product went out of stock or was disabled after it was added. */
        boolean available,
        Integer availableStock
) {
}
