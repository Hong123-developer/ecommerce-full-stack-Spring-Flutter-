package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ReviewRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AdminReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.MyReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewSummaryResponse;
import org.springframework.data.domain.Pageable;

/**
 * Star ratings and reviews. Every change to a review recomputes the product's
 * {@code ratingAverage} / {@code ratingCount} from its visible reviews in the same transaction.
 */
public interface ProductReviewService {

    // --- storefront (missing or disabled products are 404) ---

    /**
     * Visible reviews of a product.
     *
     * @param rating   only this star value (1..5), or null for all
     * @param sort     newest (default) | highest | lowest
     * @param viewerId the signed-in viewer, or null - sets {@code mine}
     */
    PageResponse<ReviewResponse> list(Long productId, Integer rating, String sort, int page, int size,
                                      Long viewerId);

    ReviewSummaryResponse summary(Long productId);

    /** Whether this shopper may review the product, and their own review. */
    MyReviewResponse mine(Long productId, Long userId, boolean admin);

    /** 403 unless they received the product (admins never may); 409 when they already reviewed it. */
    ReviewResponse create(Long productId, Long userId, boolean admin, ReviewRequest request);

    /** The author only (403 for anyone else). */
    ReviewResponse update(Long reviewId, Long userId, ReviewRequest request);

    /** The author only (403 for anyone else). */
    void delete(Long reviewId, Long userId);

    // --- admin ---

    PageResponse<AdminReviewResponse> adminList(String search, Integer rating, Boolean visible,
                                                Pageable pageable);

    AdminReviewResponse setVisible(Long reviewId, boolean visible);

    void adminDelete(Long reviewId);

    // --- housekeeping ---

    /** Removes every review the user wrote and recomputes the ratings of those products. */
    void deleteAllByUser(Long userId);
}
