package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ReviewRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.MyReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ReviewSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.exception.UnauthorizedException;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Star ratings and reviews.
 * <ul>
 *   <li>{@code GET /products/{id}/reviews?page=0&size=10&sort=newest|highest|lowest&rating=5} - public</li>
 *   <li>{@code GET /products/{id}/reviews/summary} - public</li>
 *   <li>{@code GET /products/{id}/reviews/me} - signed in: may I review it, and my review</li>
 *   <li>{@code POST /products/{id}/reviews} - a customer whose order with it was delivered</li>
 *   <li>{@code PUT|DELETE /reviews/{reviewId}} - the author only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> list(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(reviewService.list(productId, rating, sort, page, size,
                principal == null ? null : principal.getId()));
    }

    @GetMapping("/products/{productId}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> summary(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.summary(productId));
    }

    @GetMapping("/products/{productId}/reviews/me")
    public ApiResponse<MyReviewResponse> mine(@PathVariable Long productId,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        CustomUserDetails user = requireSignedIn(principal);
        return ApiResponse.ok(reviewService.mine(productId, user.getId(), isAdmin(user)));
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@PathVariable Long productId,
                                              @AuthenticationPrincipal CustomUserDetails principal,
                                              @Valid @RequestBody ReviewRequest request) {
        CustomUserDetails user = requireSignedIn(principal);
        return ApiResponse.ok("Thanks for your review",
                reviewService.create(productId, user.getId(), isAdmin(user), request));
    }

    @PutMapping("/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> update(@PathVariable Long reviewId,
                                              @AuthenticationPrincipal CustomUserDetails principal,
                                              @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok("Review updated",
                reviewService.update(reviewId, requireSignedIn(principal).getId(), request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> delete(@PathVariable Long reviewId,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        reviewService.delete(reviewId, requireSignedIn(principal).getId());
        return ApiResponse.ok("Review deleted", null);
    }

    private static CustomUserDetails requireSignedIn(CustomUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Please sign in to continue");
        }
        return principal;
    }

    private static boolean isAdmin(CustomUserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(authority -> RoleName.ROLE_ADMIN.name().equals(authority.getAuthority()));
    }
}
