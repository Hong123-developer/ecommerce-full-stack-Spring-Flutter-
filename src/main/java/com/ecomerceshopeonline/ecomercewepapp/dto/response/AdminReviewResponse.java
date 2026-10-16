package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** A review on the admin moderation page - with the author's real name and email. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReviewResponse(
        Long id,
        Long productId,
        String productName,
        /** The product's main image, or null. */
        String productImageUrl,
        int rating,
        String title,
        String comment,
        String variantLabel,
        Long authorId,
        /** Full name, for moderation. */
        String authorName,
        String authorEmail,
        String authorAvatar,
        boolean visible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
