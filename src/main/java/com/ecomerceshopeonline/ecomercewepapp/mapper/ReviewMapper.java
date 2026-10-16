package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.AdminReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductReview;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class ReviewMapper {

    /** Shown when an account's name gives nothing usable. */
    static final String FALLBACK_AUTHOR = "Customer";

    /**
     * Shopper view.
     *
     * @param viewerId the signed-in viewer, or null for a guest - sets {@code mine}
     */
    public ReviewResponse toResponse(ProductReview review, Long viewerId) {
        User author = review.getUser();
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getVariantLabel(),
                publicName(author.getFullName()),
                author.getProfileImage(),
                true,
                review.getCreatedAt(),
                review.getUpdatedAt(),
                viewerId != null && Objects.equals(author.getId(), viewerId),
                review.isVisible());
    }

    /** Moderation view: the author's full name and email. */
    public AdminReviewResponse toAdminResponse(ProductReview review) {
        Product product = review.getProduct();
        User author = review.getUser();
        return new AdminReviewResponse(
                review.getId(),
                product.getId(),
                product.getName(),
                product.getImageUrl(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getVariantLabel(),
                author.getId(),
                author.getFullName(),
                author.getEmail(),
                author.getProfileImage(),
                review.isVisible(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    /**
     * First name + last initial: "Dara Sok" -> "Dara S.", a single name stays as it is.
     * Accounts created through Google / Facebook without a name carry their email as the
     * name; that is never shown - only its first letter ("dara@x.com" -> "D.").
     */
    public static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return FALLBACK_AUTHOR;
        }
        String name = fullName.strip();
        int at = name.indexOf('@');
        if (at >= 0) {
            String local = name.substring(0, at).strip();
            return local.isEmpty() ? FALLBACK_AUTHOR : initial(local);
        }
        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + initial(parts[parts.length - 1]);
    }

    /** First character (a whole code point, so Khmer and emoji stay intact) upper-cased, plus a dot. */
    private static String initial(String word) {
        int first = word.codePointAt(0);
        return new String(Character.toChars(first)).toUpperCase(Locale.ROOT) + ".";
    }
}
