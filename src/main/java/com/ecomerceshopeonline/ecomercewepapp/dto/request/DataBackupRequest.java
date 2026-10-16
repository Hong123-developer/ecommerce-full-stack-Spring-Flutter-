package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;

/** The admin's own password again: a backup holds every customer's personal data. */
public record DataBackupRequest(

        @NotBlank(message = "Enter your password")
        String password
) {
}
