package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductLikeResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class ProductLikeController {

    private final ProductLikeService productLikeService;

    /** Toggle: like if not liked, unlike if already liked. */
    @PostMapping("/api/v1/products/{productId}/like")
    public ApiResponse<ProductLikeResponse> toggle(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long productId) {
        return ApiResponse.ok(productLikeService.toggle(principal.getId(), productId));
    }

    /** Check whether the current user has liked this product, plus total count. */
    @GetMapping("/api/v1/products/{productId}/like")
    public ApiResponse<ProductLikeResponse> status(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long productId) {
        boolean liked = productLikeService.isLiked(principal.getId(), productId);
        long count = productLikeService.countLikes(productId);
        return ApiResponse.ok(new ProductLikeResponse(productId, null, null, null, false, liked, count, null));
    }

    /** The signed-in user's wishlist, newest likes first. */
    @GetMapping("/api/v1/me/likes")
    public ApiResponse<PageResponse<ProductLikeResponse>> myLikes(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(productLikeService.myLikes(principal.getId(), pageable));
    }
}
