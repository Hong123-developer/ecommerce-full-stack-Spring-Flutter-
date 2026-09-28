package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice(),
                product.getStockQuantity(),
                product.isInStock(1),
                product.getImageUrl(),
                product.isEnabled(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public Product toEntity(ProductRequest request, Category category) {
        return Product.builder()
                .category(category)
                .name(request.name().trim())
                .description(request.description())
                .sku(request.sku().trim())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .imageUrl(request.imageUrl())
                .enabled(request.enabled() == null || request.enabled())
                .build();
    }

    public void apply(ProductRequest request, Category category, Product target) {
        target.setCategory(category);
        target.setName(request.name().trim());
        target.setDescription(request.description());
        target.setSku(request.sku().trim());
        target.setPrice(request.price());
        target.setStockQuantity(request.stockQuantity());
        target.setImageUrl(request.imageUrl());
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        }
    }
}
