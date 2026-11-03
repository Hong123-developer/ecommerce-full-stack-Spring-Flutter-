package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.ProductRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.VariantRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ProductResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.VariantResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductMapper {

    /** Admin view: every variant, including disabled ones. */
    public ProductResponse toResponse(Product product) {
        return toResponse(product, true, 0, false);
    }

    /** Shopper view: disabled variants are left out. */
    public ProductResponse toPublicResponse(Product product) {
        return toResponse(product, false, 0, false);
    }

    /** Shopper view with like data. */
    public ProductResponse toPublicResponse(Product product, long likeCount, boolean liked) {
        return toResponse(product, false, likeCount, liked);
    }

    public VariantResponse toVariantResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        boolean purchasable = variant.isEnabled() && (product == null || product.isEnabled());
        return new VariantResponse(
                variant.getId(),
                List.copyOf(variant.getOptions()),
                variant.getLabel(),
                variant.getSku(),
                product.discountedPrice(variant.getPrice()),
                variant.getPrice(),
                product.getDiscountPercent(),
                variant.getStockQuantity(),
                purchasable && variant.isInStock(1),
                variant.getImageUrl(),
                variant.isEnabled());
    }

    private ProductResponse toResponse(Product product, boolean includeDisabledVariants,
                                       long likeCount, boolean liked) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.discountedPrice(product.getPrice()),
                product.getPrice(),
                product.getDiscountPercent(),
                product.getDiscountStartDate(),
                product.getDiscountEndDate(),
                product.isDiscountActive(),
                product.getStockQuantity(),
                product.isInStock(1),
                product.getImageUrl(),
                List.copyOf(product.getGalleryUrls()),
                List.copyOf(product.getOptionNames()),
                // Without option names the product is plain: any retired variant rows left
                // behind are order history only, never shown (so an editor can't revive them).
                product.getOptionNames().isEmpty() ? List.<VariantResponse>of() : product.getVariants().stream()
                        .filter(variant -> includeDisabledVariants || variant.isEnabled())
                        .map(this::toVariantResponse)
                        .toList(),
                product.isEnabled(),
                product.getRatingAverage() == null ? BigDecimal.ZERO : product.getRatingAverage(),
                product.getRatingCount(),
                product.getShopRating(),
                // A note without stars means nothing to a shopper; the admin view keeps it editable.
                includeDisabledVariants || product.getShopRating() != null ? product.getShopRatingLabel() : null,
                likeCount,
                liked,
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
                .discountPercent(request.discountPercent() == null ? 0 : request.discountPercent())
                .stockQuantity(request.stockQuantity())
                .imageUrl(request.imageUrl())
                .galleryUrls(cleanGallery(request.galleryUrls()))
                .enabled(request.enabled() == null || request.enabled())
                .shopRating(shopRatingOrNull(request.shopRating()))
                .shopRatingLabel(blankToNull(request.shopRatingLabel()))
                .build();
    }

    public void apply(ProductRequest request, Category category, Product target) {
        target.setCategory(category);
        target.setName(request.name().trim());
        target.setDescription(request.description());
        target.setSku(request.sku().trim());
        target.setPrice(request.price());
        if (request.discountPercent() != null) {
            target.setDiscountPercent(request.discountPercent());
        }
        target.setStockQuantity(request.stockQuantity());
        target.setImageUrl(request.imageUrl());
        // Older clients don't send the gallery at all - leave it alone rather than wipe it.
        if (request.galleryUrls() != null) {
            target.getGalleryUrls().clear();
            target.getGalleryUrls().addAll(cleanGallery(request.galleryUrls()));
        }
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        }
        // Null = a client that doesn't know about the shop rating (status toggles, the wizard,
        // the variants editor...) or isn't changing it: keep it. 0 / "" clear it.
        if (request.shopRating() != null) {
            target.setShopRating(shopRatingOrNull(request.shopRating()));
        }
        if (request.shopRatingLabel() != null) {
            target.setShopRatingLabel(blankToNull(request.shopRatingLabel()));
        }
    }

    /** Copies a request onto a new or existing variant. Price/stock sync is the service's job. */
    public void applyVariant(VariantRequest request, ProductVariant target) {
        target.getOptions().clear();
        target.getOptions().addAll(request.options().stream().map(String::trim).toList());
        target.setSku(request.sku().trim());
        target.setPrice(request.price());
        target.setStockQuantity(request.stockQuantity());
        target.setImageUrl(blankToNull(request.imageUrl()));
        target.setEnabled(request.enabled() == null || request.enabled());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 0 means "no shop rating"; anything else was already checked to be a half-star value. */
    private static BigDecimal shopRatingOrNull(BigDecimal value) {
        return value == null || value.signum() == 0 ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    /** Trimmed, de-duplicated, in the order given. */
    private static List<String> cleanGallery(List<String> urls) {
        if (urls == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(urls.stream()
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .distinct()
                .toList());
    }
}
