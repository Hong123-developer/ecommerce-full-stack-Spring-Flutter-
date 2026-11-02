package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CouponRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.*;
import com.ecomerceshopeonline.ecomercewepapp.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {
    private final CouponService couponService;

    @GetMapping
    public ApiResponse<PageResponse<CouponResponse>> list(Pageable pageable) {
        var page = couponService.list(pageable);
        return ApiResponse.ok(new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponRequest request) {
        return ApiResponse.ok("Coupon created", couponService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CouponResponse> update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return ApiResponse.ok("Coupon updated", couponService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CouponResponse> status(@PathVariable Long id, @RequestParam boolean enabled) {
        return ApiResponse.ok(couponService.setEnabled(id, enabled));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ApiResponse.message("Coupon deleted");
    }
}
