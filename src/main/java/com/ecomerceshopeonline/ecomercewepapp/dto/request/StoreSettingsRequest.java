package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the store branding: every save sends the whole form, and an omitted
 * or blank optional field clears that value (the storefront then uses its built-in one).
 * <p>
 * The compact constructor trims every string and turns blank into null <em>before</em>
 * validation runs, so the length limits apply to the trimmed text and a blank optional
 * field is never checked against its email/phone/URL format.
 */
public record StoreSettingsRequest(

        @NotBlank(message = "Store name is required")
        @Size(max = 60, message = "Store name must be at most 60 characters")
        String storeName,

        @Size(max = 500, message = "Logo URL must be at most 500 characters")
        @Pattern(regexp = StoreSettingsRequest.URL_PATTERN,
                message = "Logo URL must start with / or http(s)://")
        String logoUrl,

        /** Null means "show the name" - the safe default for a logo without readable text. */
        Boolean showNameWithLogo,

        @Size(max = 200, message = "Tagline must be at most 200 characters")
        String tagline,

        @Email(message = "Support email must be a valid address")
        @Size(max = 120, message = "Support email must be at most 120 characters")
        String supportEmail,

        @Pattern(regexp = "^[0-9+()\\-\\s]{6,30}$",
                message = "Support phone must be 6-30 digits, spaces or + ( ) -")
        String supportPhone,

        @Size(max = 200, message = "Address must be at most 200 characters")
        String address
) {

    /**
     * An in-app path or an absolute http(s) URL - what the image upload endpoint returns.
     * A path may not start with {@code //} or {@code /\}: browsers read both as "another host".
     */
    public static final String URL_PATTERN = "^/(?![/\\\\]).*|^https?://.+";

    public StoreSettingsRequest {
        storeName = clean(storeName);
        logoUrl = clean(logoUrl);
        tagline = clean(tagline);
        supportEmail = clean(supportEmail);
        supportPhone = clean(supportPhone);
        address = clean(address);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
