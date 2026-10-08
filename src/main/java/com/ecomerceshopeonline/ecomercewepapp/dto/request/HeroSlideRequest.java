package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HeroSlideRequest(

        @Size(max = 60, message = "Eyebrow must be at most 60 characters")
        String eyebrow,

        @NotBlank(message = "Slide title is required")
        @Size(max = 120, message = "Slide title must be at most 120 characters")
        String title,

        @Size(max = 300, message = "Subtitle must be at most 300 characters")
        String subtitle,

        @NotBlank(message = "Image URL is required")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        @Size(max = 150, message = "Image alt text must be at most 150 characters")
        String imageAlt,

        @Size(max = 40, message = "Button label must be at most 40 characters")
        String primaryLabel,

        @Size(max = 300, message = "Link must be at most 300 characters")
        @Pattern(regexp = HeroSlideRequest.LINK_PATTERN, message = HeroSlideRequest.LINK_MESSAGE)
        String primaryLink,

        @Size(max = 40, message = "Button label must be at most 40 characters")
        String secondaryLabel,

        @Size(max = 300, message = "Link must be at most 300 characters")
        @Pattern(regexp = HeroSlideRequest.LINK_PATTERN, message = HeroSlideRequest.LINK_MESSAGE)
        String secondaryLink,

        @Size(max = 500, message = "Card 1 image URL must be at most 500 characters")
        String card1ImageUrl,

        @Size(max = 40, message = "Card 1 title must be at most 40 characters")
        String card1Title,

        @Size(max = 40, message = "Card 1 caption must be at most 40 characters")
        String card1Caption,

        @Size(max = 300, message = "Link must be at most 300 characters")
        @Pattern(regexp = HeroSlideRequest.LINK_PATTERN, message = HeroSlideRequest.LINK_MESSAGE)
        String card1Link,

        @Size(max = 500, message = "Card 2 image URL must be at most 500 characters")
        String card2ImageUrl,

        @Size(max = 40, message = "Card 2 title must be at most 40 characters")
        String card2Title,

        @Size(max = 40, message = "Card 2 caption must be at most 40 characters")
        String card2Caption,

        @Size(max = 300, message = "Link must be at most 300 characters")
        @Pattern(regexp = HeroSlideRequest.LINK_PATTERN, message = HeroSlideRequest.LINK_MESSAGE)
        String card2Link,

        /**
         * Optional {@code #RRGGBB} accent, either case; stored upper-case. Like the other
         * optional text fields, null or blank stores null - on update too, so it clears.
         */
        @Pattern(regexp = HeroSlideRequest.ACCENT_COLOR_PATTERN, message = HeroSlideRequest.ACCENT_COLOR_MESSAGE)
        String accentColor,

        /** Null on create means "append after the last slide"; null on update means "unchanged". */
        @Min(value = 0, message = "Sort order cannot be negative")
        Integer sortOrder,

        /** Null on create means "enabled"; null on update means "unchanged". */
        Boolean enabled
) {

    /**
     * Blank, an in-app path, or an absolute http(s) URL. Anything else - notably
     * {@code javascript:} - would become a script link on the storefront. A path may not
     * start with {@code //} or {@code /\} either: browsers read both as "another host".
     */
    public static final String LINK_PATTERN = "^\\s*$|^/(?![/\\\\]).*|^https?://.+";

    public static final String LINK_MESSAGE = "Link must start with / or http(s)://";

    /**
     * Blank (no accent), or exactly {@code #} plus six hex digits. No surrounding spaces,
     * no 3-digit shorthand and no color names - the value goes straight into CSS.
     */
    public static final String ACCENT_COLOR_PATTERN = "^\\s*$|^#[0-9A-Fa-f]{6}$";

    public static final String ACCENT_COLOR_MESSAGE = "Banner color must be a hex color like #D97706";
}
