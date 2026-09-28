package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 120, message = "Category name must be at most 120 characters")
        String name,

        String description,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        /** Null on create means "enabled". */
        Boolean enabled
) {
}
