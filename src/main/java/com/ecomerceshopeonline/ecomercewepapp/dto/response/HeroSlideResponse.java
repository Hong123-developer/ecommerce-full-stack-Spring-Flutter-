package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.time.LocalDateTime;

public record HeroSlideResponse(
        Long id,
        String eyebrow,
        String title,
        String subtitle,
        String imageUrl,
        String imageAlt,
        String primaryLabel,
        String primaryLink,
        String secondaryLabel,
        String secondaryLink,
        String card1ImageUrl,
        String card1Title,
        String card1Caption,
        String card1Link,
        String card2ImageUrl,
        String card2Title,
        String card2Caption,
        String card2Link,
        /** Upper-case {@code #RRGGBB}, or null when the slide has no accent of its own. */
        String accentColor,
        int sortOrder,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
