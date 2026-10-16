package com.ecomerceshopeonline.ecomercewepapp.controller;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Public catalogue browsing, searching and filtering. No authentication required.
 * <p>
 * Example: {@code /api/v1/products?search=shirt&categoryId=3&minPrice=5&maxPrice=50&page=0&size=20&sort=price,asc}
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> browse(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            // The display order set in admin; ties fall back to the newest product.
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiResponse.ok(
                productService.browse(search, categoryId, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getPublic(id));
    }
}
