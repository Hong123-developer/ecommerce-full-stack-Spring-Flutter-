package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddToCartRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateCartItemRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(cartService.getCart(principal.getId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@AuthenticationPrincipal CustomUserDetails principal,
                                             @Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.ok("Added to cart", cartService.addItem(principal.getId(), request));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.ok("Cart updated",
                cartService.updateItem(principal.getId(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long itemId) {
        return ApiResponse.ok("Removed from cart",
                cartService.removeItem(principal.getId(), itemId));
    }

    @DeleteMapping
    public ApiResponse<Void> clear(@AuthenticationPrincipal CustomUserDetails principal) {
        cartService.clear(principal.getId());
        return ApiResponse.message("Cart cleared");
    }
}
