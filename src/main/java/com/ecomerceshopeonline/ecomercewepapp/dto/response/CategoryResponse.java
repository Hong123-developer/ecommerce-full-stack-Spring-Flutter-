package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
