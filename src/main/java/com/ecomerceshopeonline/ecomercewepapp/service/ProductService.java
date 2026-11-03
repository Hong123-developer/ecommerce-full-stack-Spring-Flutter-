package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.MoveDirection;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.DiscountRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductService {

    /** Public catalogue. Every filter is optional. */
    PageResponse<ProductResponse> browse(String search, Long categoryId,
                                         BigDecimal minPrice, BigDecimal maxPrice,
                                         Pageable pageable);

    ProductResponse getPublic(Long id);

    // --- admin ---

    PageResponse<ProductResponse> listAll(String search, Pageable pageable);

    ProductResponse getById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse setEnabled(Long id, boolean enabled);

    ProductResponse setDiscount(Long id, DiscountRequest request);

    /** Swaps the product with its neighbour in the display order; 400 when it is already first / last. */
    ProductResponse move(Long id, MoveDirection direction);

    void delete(Long id);
}
