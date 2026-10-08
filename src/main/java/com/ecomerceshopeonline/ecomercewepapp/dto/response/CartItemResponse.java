package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        /** Null for a product without variants. */
        Long variantId,
        /** e.g. "iPhone 14 Pro · 256GB · Purple"; null without a variant. */
        String variantLabel,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        /** False when the product (or variant) went out of stock or was disabled after it was added. */
        boolean available,
        Integer availableStock
) {
}
