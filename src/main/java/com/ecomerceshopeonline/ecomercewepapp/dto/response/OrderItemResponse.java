package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        /** Null if the product has since been removed from the catalogue. */
        Long productId,
        String productName,
        /** Null for a product without variants. */
        Long variantId,
        /** Snapshot taken at checkout, e.g. "iPhone 14 Pro · 256GB · Purple". */
        String variantLabel,
        /** Variant code (or product SKU) at checkout; null on orders placed before it was recorded. */
        String sku,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
