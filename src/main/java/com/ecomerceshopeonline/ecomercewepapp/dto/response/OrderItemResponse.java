package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        /** Null if the product has since been removed from the catalogue. */
        Long productId,
        String productName,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
