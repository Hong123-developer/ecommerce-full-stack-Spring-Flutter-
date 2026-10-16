package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * Permanently deletes the chosen groups. {@code confirm} must be the word DELETE, typed by
 * hand, and {@code password} the acting admin's own.
 */
public record DataDeleteRequest(

        @NotBlank(message = "Enter your password")
        String password,

        @NotBlank(message = "Type DELETE to confirm")
        String confirm,

        @NotEmpty(message = "Choose what to delete")
        Set<DataGroup> groups
) {
}
