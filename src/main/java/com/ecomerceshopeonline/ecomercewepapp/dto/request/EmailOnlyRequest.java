package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Shared by "resend verification code" and "forgot password". */
public record EmailOnlyRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email
) {
}
