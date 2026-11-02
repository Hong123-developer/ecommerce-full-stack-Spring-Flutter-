package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.AdminReviewResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ProductReviewService reviewService;

    /** {@code ?search=case&rating=1&visible=false} - all optional; newest first. */
    @GetMapping
    public ApiResponse<PageResponse<AdminReviewResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean visible,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(reviewService.adminList(search, rating, visible, pageable));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<AdminReviewResponse> setVisible(@PathVariable Long id,
                                                       @RequestParam boolean visible) {
        return ApiResponse.ok(visible ? "Review shown" : "Review hidden",
                reviewService.setVisible(id, visible));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reviewService.adminDelete(id);
        return ApiResponse.ok("Review deleted", null);
    }
}
