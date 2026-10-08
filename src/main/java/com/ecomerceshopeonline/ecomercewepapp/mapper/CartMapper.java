package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartItemResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Cart;
import com.ecomerceshopeonline.ecomercewepapp.entity.CartItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {

    public CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new CartResponse(
                cart.getId(),
                items,
                items.size(),
                items.stream().mapToInt(CartItemResponse::quantity).sum(),
                items.stream().map(CartItemResponse::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                !items.isEmpty() && items.stream().allMatch(CartItemResponse::available));
    }

    public CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();
        if (variant == null) {
            boolean available = product.isEnabled() && product.isInStock(item.getQuantity());
            return new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    null,
                    null,
                    product.getSku(),
                    product.getImageUrl(),
                    product.getPrice(),
                    item.getQuantity(),
                    item.getLineTotal(),
                    available,
                    product.getStockQuantity());
        }

        // A chosen option: code, photo, price and stock all come from the variant.
        boolean available = product.isEnabled() && variant.isEnabled()
                && variant.isInStock(item.getQuantity());
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                variant.getId(),
                variant.getLabel(),
                variant.getSku(),
                variant.getImageUrl() != null ? variant.getImageUrl() : product.getImageUrl(),
                variant.getPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                available,
                variant.getStockQuantity());
    }
}
