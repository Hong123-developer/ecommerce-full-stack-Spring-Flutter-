package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.VerifyPaymentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    /** Doubles as the payment history view: {@code ?status=PAID&search=...}. */
    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(paymentService.adminList(status, search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.adminGet(id));
    }

    /** Manual confirmation - cash on delivery, or a transfer spotted on a statement. */
    @PatchMapping("/{id}/verify")
    public ApiResponse<PaymentResponse> verify(@PathVariable Long id,
                                               @Valid @RequestBody(required = false)
                                               VerifyPaymentRequest request) {
        return ApiResponse.ok("Payment verified", paymentService.adminVerify(id, request));
    }

    /** Ask Bakong directly instead of trusting the shopper's poll. */
    @PostMapping("/{id}/check-bakong")
    public ApiResponse<PaymentResponse> checkWithBakong(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.adminCheckWithBakong(id));
    }
}
