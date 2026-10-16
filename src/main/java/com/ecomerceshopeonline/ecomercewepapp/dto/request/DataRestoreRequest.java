package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Replaces all shop data with a backup file's contents. {@code backup} is the downloaded file,
 * as is; {@code confirm} must be the word RESTORE.
 */
public record DataRestoreRequest(

        @NotBlank(message = "Enter your password")
        String password,

        @NotBlank(message = "Type RESTORE to confirm")
        String confirm,

        @NotNull(message = "Choose a backup file")
        Map<String, Object> backup
) {
}
