package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.StoreSettingsResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Store branding for the storefront header and footer. No authentication required. */
@RestController
@RequestMapping("/api/v1/store-settings")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ApiResponse<StoreSettingsResponse> get() {
        return ApiResponse.ok(storeSettingsService.get());
    }
}
