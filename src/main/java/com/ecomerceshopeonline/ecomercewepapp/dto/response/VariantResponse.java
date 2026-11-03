package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record VariantResponse(
        Long id,
        /** Values in the order of the product's optionNames. */
        List<String> options,
        /** Options joined by " · ", e.g. "iPhone 14 Pro · 256GB · Purple". */
        String label,
        String sku,
        BigDecimal price,
        BigDecimal originalPrice,
        int discountPercent,
        Integer stockQuantity,
        /** Stock left, the variant is enabled and so is its product. */
        boolean inStock,
        /** Null when the variant uses the product's main image. */
        String imageUrl,
        boolean enabled
) {
}
