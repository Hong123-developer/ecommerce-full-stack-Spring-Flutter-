package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CouponCodeRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CouponApplicationResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.CartService;
import com.ecomerceshopeonline.ecomercewepapp.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;
    private final CartService cartService;

    @PostMapping("/validate")
    public ApiResponse<CouponApplicationResponse> validate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CouponCodeRequest request) {
        var cart = cartService.requireCartEntity(principal.getId());
        return ApiResponse.ok(couponService.preview(principal.getId(), request.code(), cartSubtotal(cart)));
    }

    private static java.math.BigDecimal cartSubtotal(com.ecomerceshopeonline.ecomercewepapp.entity.Cart cart) {
        return cart.getItems().stream().map(item -> {
            var price = item.getVariant() == null
                    ? item.getProduct().discountedPrice(item.getProduct().getPrice())
                    : item.getProduct().discountedPrice(item.getVariant().getPrice());
            return price.multiply(java.math.BigDecimal.valueOf(item.getQuantity()));
        }).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
