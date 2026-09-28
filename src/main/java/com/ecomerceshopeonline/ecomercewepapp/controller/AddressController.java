package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddressRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AddressResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(addressService.list(principal.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AddressResponse> get(@AuthenticationPrincipal CustomUserDetails principal,
                                            @PathVariable Long id) {
        return ApiResponse.ok(addressService.get(principal.getId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address added", addressService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address updated",
                addressService.update(principal.getId(), id, request));
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<AddressResponse> setDefault(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        return ApiResponse.ok("Default address set",
                addressService.setDefault(principal.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                    @PathVariable Long id) {
        addressService.delete(principal.getId(), id);
        return ApiResponse.message("Address deleted");
    }
}
