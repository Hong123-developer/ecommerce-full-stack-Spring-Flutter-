package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * The single place that keeps a variant product's own price and stock in line with its
 * variants, so the catalogue's price filter, sorting and "in stock" checks keep working on
 * the product row:
 * <ul>
 *   <li>price = the lowest price among enabled variants ("from $100")</li>
 *   <li>stock = the sum of enabled variants' stock</li>
 * </ul>
 * Only a <em>variant product</em> is touched: one with option names and at least one enabled
 * variant. Any other product - including one whose variants were all retired (disabled because
 * they appear in past orders) - is a plain product whose price and stock are what the admin typed.
 */
@Component
@RequiredArgsConstructor
public class ProductVariantSync {

    private final ProductRepository productRepository;

    /** True when the product's price and stock are derived from its variants. */
    public static boolean isVariantProduct(Product product) {
        return !product.getOptionNames().isEmpty() && product.hasEnabledVariants();
    }

    /** In-memory version, used after the admin creates or edits a product. */
    public void applyToProduct(Product product) {
        if (!isVariantProduct(product)) {
            return;
        }
        List<ProductVariant> enabled = product.getVariants().stream()
                .filter(ProductVariant::isEnabled)
                .toList();
        product.setPrice(enabled.stream()
                .map(ProductVariant::getPrice)
                .min(Comparator.naturalOrder())
                .orElseThrow());
        product.setStockQuantity(enabled.stream().mapToInt(ProductVariant::getStockQuantity).sum());
    }

    /**
     * Database version: re-derives the stock of a variant product from its enabled variants.
     * A no-op for a product without enabled variants. Checkout and cancellation do NOT use
     * this (they apply relative changes, see OrderServiceImpl); it is for admin-side repairs.
     */
    public void resyncStock(Long productId) {
        productRepository.syncStockFromVariants(productId);
    }
}
