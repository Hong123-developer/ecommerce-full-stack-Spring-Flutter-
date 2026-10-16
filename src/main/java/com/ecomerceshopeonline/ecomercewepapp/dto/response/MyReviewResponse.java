package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether the signed-in shopper may write a review of a product, and their own review if any.
 *
 * @param canReview true only when they received the product and have not reviewed it yet
 * @param reason    why not; null when {@code canReview}
 * @param review    their own review (also when an admin hid it), or null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MyReviewResponse(
        boolean canReview,
        Reason reason,
        ReviewResponse review
) {

    public enum Reason {
        /** No order with this product (administrators are always in this case). */
        NOT_PURCHASED,
        /** Ordered, but no order with it has been delivered yet. */
        NOT_DELIVERED,
        /** They already have a review - edit it instead. */
        ALREADY_REVIEWED
    }
}
