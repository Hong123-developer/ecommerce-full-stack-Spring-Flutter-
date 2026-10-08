package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create or update a delivery option. Text fields are full replacement: an omitted or blank
 * optional field clears it. {@code enabled} and {@code sortOrder} are the exception - null
 * leaves them as they are on update.
 * <p>
 * The compact constructor trims every string and turns blank into null <em>before</em>
 * validation runs, so the length limits apply to the trimmed text and a blank logo URL is
 * never checked against the URL format.
 */
public record DeliveryOptionRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @Size(max = 200, message = "Description must be at most 200 characters")
        String description,

        @NotNull(message = "Type is required")
        DeliveryType type,

        @NotNull(message = "Fee is required")
        @DecimalMin(value = "0.00", message = "Fee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Fee must have at most 2 decimal places")
        BigDecimal fee,

        @Size(max = 60, message = "Estimated time must be at most 60 characters")
        String estimatedTime,

        @Size(max = 500, message = "Logo URL must be at most 500 characters")
        @Pattern(regexp = DeliveryOptionRequest.URL_PATTERN,
                message = "Logo URL must start with / or http(s)://")
        String logoUrl,

        /** Null on create means "enabled"; null on update means "unchanged". */
        Boolean enabled,

        /** Null on create means "after the last option"; null on update means "unchanged". */
        @Min(value = 0, message = "Sort order cannot be negative")
        Integer sortOrder
) {

    /**
     * An in-app path or an absolute http(s) URL - what the image upload endpoint returns.
     * A path may not start with {@code //} or {@code /\}: browsers read both as "another host".
     */
    public static final String URL_PATTERN = "^/(?![/\\\\]).*|^https?://.+";

    public DeliveryOptionRequest {
        name = clean(name);
        description = clean(description);
        estimatedTime = clean(estimatedTime);
        logoUrl = clean(logoUrl);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
