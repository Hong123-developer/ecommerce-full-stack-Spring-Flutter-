package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.PaymentAccountRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PaymentAccountResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.PaymentAccount;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentAccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The bank account shown on the payment page. Any signed-in user may read it (it is what a
 * shopper pays into); only admins change it.
 */
@RestController
@RequiredArgsConstructor
public class PaymentAccountController {

    private final PaymentAccountRepository repository;

    @GetMapping("/api/v1/payment-account")
    @Transactional(readOnly = true)
    public ApiResponse<PaymentAccountResponse> get() {
        return ApiResponse.ok(current());
    }

    @GetMapping("/api/v1/admin/payment-account")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ApiResponse<PaymentAccountResponse> adminGet() {
        return ApiResponse.ok(current());
    }

    @PutMapping("/api/v1/admin/payment-account")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ApiResponse<PaymentAccountResponse> save(@Valid @RequestBody PaymentAccountRequest request) {
        PaymentAccount account = repository.findSingleton()
                .orElseGet(() -> PaymentAccount.builder().id(PaymentAccount.SINGLETON_ID).build());
        account.setBankName(request.bankName());
        account.setAccountName(request.accountName());
        account.setUsdAccountNumber(request.usdAccountNumber());
        account.setKhrAccountNumber(request.khrAccountNumber());
        // Flushed now so the auditing listener has stamped updatedAt for the response.
        return ApiResponse.ok("Payment account saved", PaymentAccountResponse.of(repository.saveAndFlush(account)));
    }

    private PaymentAccountResponse current() {
        return repository.findSingleton().map(PaymentAccountResponse::of).orElseGet(PaymentAccountResponse::unsaved);
    }
}
