package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DataBackupRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.DataDeleteRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.DataRestoreRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DataSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.DataManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin › Settings › Data. Backup, restore and delete are POSTs carrying the admin's
 * password: a backup holds every customer's personal data, and the other two cannot be undone.
 */
@RestController
@RequestMapping("/api/v1/admin/data")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDataController {

    private final DataManagementService dataService;

    @GetMapping("/summary")
    public ApiResponse<DataSummaryResponse> summary() {
        return ApiResponse.ok(dataService.summary());
    }

    @PostMapping("/backup")
    public ApiResponse<Map<String, Object>> backup(@AuthenticationPrincipal CustomUserDetails principal,
                                                   @Valid @RequestBody DataBackupRequest request) {
        return ApiResponse.ok(dataService.backup(principal.getId(), request.password()));
    }

    @PostMapping("/restore")
    public ApiResponse<Map<String, Integer>> restore(@AuthenticationPrincipal CustomUserDetails principal,
                                                     @Valid @RequestBody DataRestoreRequest request) {
        return ApiResponse.ok("Backup restored", dataService.restore(
                principal.getId(), request.password(), request.confirm(), request.backup()));
    }

    @PostMapping("/delete")
    public ApiResponse<Map<String, Integer>> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                                    @Valid @RequestBody DataDeleteRequest request) {
        return ApiResponse.ok("Data deleted", dataService.delete(
                principal.getId(), request.password(), request.confirm(), request.groups()));
    }
}
