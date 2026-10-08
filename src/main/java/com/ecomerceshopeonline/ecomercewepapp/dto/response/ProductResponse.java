package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        /** Extra photos after imageUrl, in display order; never null. */
        List<String> galleryUrls,
        /** e.g. [Model, Storage, Color]; empty for a product without variants. */
        List<String> optionNames,
        /** Empty without variants. Public endpoints list enabled variants only. */
        List<VariantResponse> variants,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
