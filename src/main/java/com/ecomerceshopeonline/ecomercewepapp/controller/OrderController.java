package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CheckoutRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderTrackingResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.InvoiceResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Converts the cart into an order and reserves stock. */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> checkout(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.ok("Order placed", orderService.checkout(principal.getId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> listOwn(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.listOwn(principal.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOwn(@AuthenticationPrincipal CustomUserDetails principal,
                                             @PathVariable Long id) {
        return ApiResponse.ok(orderService.getOwn(principal.getId(), id));
    }

    @GetMapping("/{id}/tracking")
    public ApiResponse<OrderTrackingResponse> tracking(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(orderService.trackingOwn(principal.getId(), id));
    }

    @GetMapping("/{id}/invoice")
    public ApiResponse<InvoiceResponse> invoice(@AuthenticationPrincipal CustomUserDetails principal,
                                                @PathVariable Long id) {
        return ApiResponse.ok(orderService.invoiceOwn(principal.getId(), id));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal CustomUserDetails principal,
                                             @PathVariable Long id) {
        return ApiResponse.ok("Order cancelled", orderService.cancelOwn(principal.getId(), id));
    }
}
