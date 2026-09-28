package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{8,30}$", message = "Phone must be 8-30 digits")
        String phone,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String profileImage
) {
}
