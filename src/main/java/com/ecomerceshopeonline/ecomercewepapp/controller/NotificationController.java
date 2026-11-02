package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.NotificationResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Newest first. */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.listOwn(principal.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(principal.getId())));
    }

    @PatchMapping("/{id:\\d+}/read")
    public ApiResponse<NotificationResponse> markRead(@AuthenticationPrincipal CustomUserDetails principal,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(principal.getId(), id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("All notifications marked as read",
                Map.of("updated", notificationService.markAllRead(principal.getId())));
    }
}
