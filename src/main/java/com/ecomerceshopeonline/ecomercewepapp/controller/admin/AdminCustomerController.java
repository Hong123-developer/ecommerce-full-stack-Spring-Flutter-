package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CustomerResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    /** {@code ?search=sokha&enabled=false} - both optional. */
    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(adminCustomerService.list(search, enabled, pageable));
    }

    /** Includes order count, lifetime spend and saved addresses. */
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(adminCustomerService.get(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CustomerResponse> setEnabled(@PathVariable Long id,
                                                    @RequestParam boolean enabled) {
        return ApiResponse.ok(enabled ? "Customer enabled" : "Customer disabled",
                adminCustomerService.setEnabled(id, enabled));
    }

    /**
     * Permanent removal - only for customers without orders (409 otherwise; disable instead).
     * Administrators, including the caller, cannot be deleted here.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        adminCustomerService.delete(id, principal == null ? null : principal.getId());
        return ApiResponse.ok("Customer deleted", null);
    }

    /** Registration history: signups per day over the trailing window. */
    @GetMapping("/registrations")
    public ApiResponse<List<DashboardResponse.DailyCount>> registrations(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(adminCustomerService.registrationHistory(days));
    }
}
