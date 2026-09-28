package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{8,30}$", message = "Phone must be 8-30 digits")
        String phone,

        @NotBlank(message = "Password is required")
        // 72 is BCrypt's hard limit - anything beyond it is silently ignored.
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit")
        String password
) {
}
