package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.ProductReview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of creating or editing a review. Only the stars are required.
 * <p>
 * The compact constructor trims the text and turns blank into null <em>before</em>
 * validation runs, so the length limits apply to the trimmed text.
 */
public record ReviewRequest(

        @NotNull(message = "Choose a star rating")
        @Min(value = ProductReview.MIN_RATING, message = "Rating must be between 1 and 5")
        @Max(value = ProductReview.MAX_RATING, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = ProductReview.MAX_TITLE, message = "Title must be at most 100 characters")
        String title,

        @Size(max = ProductReview.MAX_COMMENT, message = "Review must be at most 1000 characters")
        String comment
) {

    public ReviewRequest {
        title = clean(title);
        comment = clean(comment);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
