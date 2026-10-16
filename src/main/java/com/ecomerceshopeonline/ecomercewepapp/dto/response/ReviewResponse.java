package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * A review as shoppers see it. The author is only ever shown as first name + last initial
 * ("Dara S.") - never the email or the full name.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewResponse(
        Long id,
        int rating,
        /** Null when the author gave none. */
        String title,
        /** Null when the author gave none. */
        String comment,
        /** Options of the delivered order line, e.g. "256GB · Purple"; null without variants. */
        String variantLabel,
        String authorName,
        /** The author's profile photo, or null. */
        String authorAvatar,
        /** Always true: only customers whose order was delivered can review. */
        boolean verifiedPurchase,
        LocalDateTime createdAt,
        /** The author's last edit (equal to createdAt until they edit). */
        LocalDateTime updatedAt,
        /** True when the signed-in viewer wrote it; false for guests. */
        boolean mine,
        /** False only on the author's own review after an admin hid it (the public list has visible ones only). */
        boolean visible
) {
}
