package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned by the like/unlike toggle and by the "My Wishlist" listing.
 */
public record ProductLikeResponse(
        Long productId,
        String productName,
        BigDecimal productPrice,
        String productImageUrl,
        boolean inStock,
        boolean liked,
        long likeCount,
        LocalDateTime likedAt
) {
}
