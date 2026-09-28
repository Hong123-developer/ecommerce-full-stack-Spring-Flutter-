package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CategoryRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CategoryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    /** Public listing - enabled categories only. */
    PageResponse<CategoryResponse> browse(Pageable pageable);

    CategoryResponse getPublic(Long id);

    // --- admin ---

    PageResponse<CategoryResponse> listAll(Pageable pageable);

    CategoryResponse getById(Long id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    CategoryResponse setEnabled(Long id, boolean enabled);

    void delete(Long id);
}
