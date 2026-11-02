package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.HeroSlideOrderRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.HeroSlideRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.HeroSlideResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.HeroSlideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/admin/hero-slides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHeroSlideController {

    private final HeroSlideService heroSlideService;

    @GetMapping
    public ApiResponse<List<HeroSlideResponse>> listAll() {
        return ApiResponse.ok(heroSlideService.listAll());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<HeroSlideResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(heroSlideService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeroSlideResponse> create(@Valid @RequestBody HeroSlideRequest request) {
        return ApiResponse.ok("Slide created", heroSlideService.create(request));
    }

    @PutMapping("/order")
    public ApiResponse<List<HeroSlideResponse>> reorder(
            @Valid @RequestBody HeroSlideOrderRequest request) {
        return ApiResponse.ok("Slide order updated", heroSlideService.reorder(request.ids()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<HeroSlideResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody HeroSlideRequest request) {
        return ApiResponse.ok("Slide updated", heroSlideService.update(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public ApiResponse<HeroSlideResponse> setEnabled(@PathVariable Long id,
                                                     @RequestParam boolean enabled) {
        return ApiResponse.ok(enabled ? "Slide enabled" : "Slide disabled",
                heroSlideService.setEnabled(id, enabled));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        heroSlideService.delete(id);
        return ApiResponse.message("Slide deleted");
    }
}
