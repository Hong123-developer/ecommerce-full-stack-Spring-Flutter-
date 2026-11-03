package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(

        @NotNull(message = "Category is required")
        Long categoryId,

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name must be at most 200 characters")
        String name,

        String description,

        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price,

        @Min(value = 0, message = "Discount cannot be negative")
        @Max(value = 100, message = "Discount cannot exceed 100 percent")
        Integer discountPercent,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        Integer stockQuantity,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        /** Extra photos after the main image. Null on update means "unchanged"; [] clears them. */
        @Size(max = ProductRequest.MAX_GALLERY, message = "A product can have at most 8 extra images")
        List<
                @NotBlank(message = "Gallery image URL cannot be blank")
                @Size(max = 500, message = "Gallery image URL must be at most 500 characters")
                @Pattern(regexp = ProductRequest.URL_PATTERN, message = "Gallery image URL must start with / or http(s)://")
                String> galleryUrls,

        /** Null on create means "enabled". */
        Boolean enabled,

        /**
         * Option names such as [Model, Storage, Color]. Null on update means "unchanged".
         * Uniqueness (ignoring case) is checked by the service.
         */
        @Size(max = ProductRequest.MAX_OPTION_NAMES, message = "A product can have at most 3 options")
        List<
                @NotBlank(message = "Option name cannot be blank")
                @Size(max = 30, message = "Option name must be at most 30 characters")
                String> optionNames,

        /**
         * Buyable combinations. Null on update means "unchanged"; [] removes them all. When
         * present, the product price and stock are derived from the enabled variants and the
         * top-level price / stockQuantity are ignored.
         */
        @Size(max = ProductRequest.MAX_VARIANTS, message = "A product can have at most 100 variants")
        List<@NotNull(message = "Variant cannot be null") @Valid VariantRequest> variants,

        /**
         * The shop's own star score, shown to shoppers as "Shop rating" and never mixed into
         * customer reviews: 0.5 to 5 in half stars. 0 removes it. Null on update means
         * "unchanged" (so a client that doesn't know about it can't wipe it); null on create = none.
         */
        @ShopRatingValue
        BigDecimal shopRating,

        /**
         * Short note shown next to the shop rating, e.g. "Rated on our Facebook page". Trimmed.
         * Null on update means "unchanged"; blank clears it.
         */
        @Size(max = ProductRequest.MAX_SHOP_RATING_LABEL, message = "Shop rating label must be at most 60 characters")
        String shopRatingLabel
) {

    public static final int MAX_GALLERY = 8;
    public static final int MAX_OPTION_NAMES = 3;
    public static final int MAX_VARIANTS = 100;
    public static final int MAX_SHOP_RATING_LABEL = 60;

    /**
     * An in-app path (not "//host" or "/\\host") or an absolute http(s) URL. Leading
     * spaces are allowed because the mapper trims each URL before storing it.
     */
    public static final String URL_PATTERN = "^\\s*(/(?![/\\\\])|https?://).+";

}
