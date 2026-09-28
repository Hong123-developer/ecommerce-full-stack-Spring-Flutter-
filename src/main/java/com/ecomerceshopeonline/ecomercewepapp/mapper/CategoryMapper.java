package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CategoryRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CategoryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                category.isEnabled(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    public Category toEntity(CategoryRequest request) {
        return Category.builder()
                .name(request.name().trim())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .enabled(request.enabled() == null || request.enabled())
                .build();
    }

    /** In-place update of a managed entity - id and audit columns are left alone. */
    public void apply(CategoryRequest request, Category target) {
        target.setName(request.name().trim());
        target.setDescription(request.description());
        target.setImageUrl(request.imageUrl());
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        }
    }
}
