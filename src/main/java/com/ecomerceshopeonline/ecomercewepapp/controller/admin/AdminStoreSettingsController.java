package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.StoreSettingsRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.StoreSettingsResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.StoreSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Store branding management. The whole {@code /api/v1/admin/**} tree already requires
 * ROLE_ADMIN in SecurityConfig; the class-level annotation states it locally too,
 * so moving the class cannot silently un-protect it.
 * <p>
 * The logo is uploaded first through {@code POST /api/v1/admin/uploads/images}; the
 * returned {@code url} is then sent as {@code logoUrl} here.
 */
@RestController
@RequestMapping("/api/v1/admin/store-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ApiResponse<StoreSettingsResponse> get() {
        return ApiResponse.ok(storeSettingsService.get());
    }

    @PutMapping
    public ApiResponse<StoreSettingsResponse> save(@Valid @RequestBody StoreSettingsRequest request) {
        return ApiResponse.ok("Store settings saved", storeSettingsService.save(request));
    }
}
