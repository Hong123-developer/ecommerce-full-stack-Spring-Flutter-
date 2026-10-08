package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.HeroSlideResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.HeroSlideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Home-page hero carousel for the storefront. No authentication required. */
@RestController
@RequestMapping("/api/v1/hero-slides")
@RequiredArgsConstructor
public class HeroSlideController {

    private final HeroSlideService heroSlideService;

    @GetMapping
    public ApiResponse<List<HeroSlideResponse>> browse() {
        return ApiResponse.ok(heroSlideService.browse());
    }
}
