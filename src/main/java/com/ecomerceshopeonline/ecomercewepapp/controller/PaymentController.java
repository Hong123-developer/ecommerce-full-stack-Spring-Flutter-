package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Start paying an order. For KHQR the response carries {@code data.khqr.qrImage}
     * (a PNG data URI) and {@code data.khqr.md5}.
     */
    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> pay(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long orderId) {
        return ApiResponse.ok("Payment started", paymentService.pay(principal.getId(), orderId));
    }

    /**
     * Poll after showing the QR. Returns the payment with {@code paymentStatus} flipped to
     * PAID once Bakong confirms; the order moves to CONFIRMED at the same moment.
     */
    @PostMapping("/{id}/check")
    public ApiResponse<PaymentResponse> checkStatus(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.checkStatus(principal.getId(), id));
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> listOwn(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(paymentService.listOwn(principal.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getOwn(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.getOwn(principal.getId(), id));
    }
}
