package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.MoveDirection;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    /**
     * Unlike the public endpoint, this includes disabled products. Defaults to the display
     * order set by {@link #move}; ties fall back to the newest product.
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> listAll(
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiResponse.ok(productService.listAll(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Product created", productService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Product updated", productService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductResponse> setEnabled(@PathVariable Long id,
                                                   @RequestParam boolean enabled) {
        return ApiResponse.ok(enabled ? "Product enabled" : "Product disabled",
                productService.setEnabled(id, enabled));
    }

    /** {@code ?direction=UP|DOWN} - one step in the display order the shop lists products in. */
    @PatchMapping("/{id}/move")
    public ApiResponse<ProductResponse> move(@PathVariable Long id,
                                             @RequestParam MoveDirection direction) {
        return ApiResponse.ok(direction == MoveDirection.UP ? "Product moved up" : "Product moved down",
                productService.move(id, direction));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.message("Product deleted");
    }
}
