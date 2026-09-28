package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final StatisticsService statisticsService;

    /** Everything the dashboard needs in one call. {@code ?days=30} sizes the charts. */
    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(statisticsService.dashboard(days));
    }
}
