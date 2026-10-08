package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * One option combination inside {@link ProductRequest#variants()}. {@code options} must have
 * the same length and order as the product's option names.
 */
public record VariantRequest(

        /** An existing variant of this product to update; omit for a new one. */
        Long id,

        @NotNull(message = "Variant options are required")
        @Size(min = 1, max = ProductRequest.MAX_OPTION_NAMES,
                message = "A variant needs one value per option")
        List<
                @NotBlank(message = "Option value cannot be blank")
                @Size(max = 60, message = "Option value must be at most 60 characters")
                String> options,

        @NotBlank(message = "Variant code is required")
        @Size(max = 64, message = "Variant code must be at most 64 characters")
        String sku,

        @NotNull(message = "Variant price is required")
        @DecimalMin(value = "0.00", message = "Variant price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Variant price must have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "Variant stock is required")
        @Min(value = 0, message = "Variant stock cannot be negative")
        Integer stockQuantity,

        @Size(max = 500, message = "Variant image URL must be at most 500 characters")
        @Pattern(regexp = ProductRequest.URL_PATTERN, message = "Variant image URL must start with / or http(s)://")
        String imageUrl,

        /** Null means enabled. */
        Boolean enabled
) {
}
