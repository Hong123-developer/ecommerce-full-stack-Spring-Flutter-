package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductLikeResponse;
import org.springframework.data.domain.Pageable;

public interface ProductLikeService {

    /** Toggle: like if not yet liked, unlike if already liked. Returns the new state. */
    ProductLikeResponse toggle(Long userId, Long productId);

    /** Is this product liked by the user? */
    boolean isLiked(Long userId, Long productId);

    /** Total likes on a product. */
    long countLikes(Long productId);

    /** The user's wishlist (liked products), newest first. */
    PageResponse<ProductLikeResponse> myLikes(Long userId, Pageable pageable);
}
