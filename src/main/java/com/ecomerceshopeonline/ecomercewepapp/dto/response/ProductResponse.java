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
        /** Average stars of the visible reviews, one decimal; 0 when there are none. */
        BigDecimal rating,
        /** Number of visible reviews. */
        int reviewCount,
        /**
         * The shop's own star score (0.5-5, half stars), to be shown labelled "Shop rating",
         * separate from the customer rating above; null when the shop hasn't set one.
         */
        BigDecimal shopRating,
        /** Optional note for the shop rating; null when none (public views: whenever shopRating is). */
        String shopRatingLabel,
        /** Total number of users who liked this product. */
        long likeCount,
        /** Whether the currently authenticated user has liked this product (false for anonymous). */
        boolean liked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

