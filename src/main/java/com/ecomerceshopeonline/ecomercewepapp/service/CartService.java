package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddToCartRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateCartItemRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CartResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Cart;

public interface CartService {

    CartResponse getCart(Long userId);

    CartResponse addItem(Long userId, AddToCartRequest request);

    CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request);

    CartResponse removeItem(Long userId, Long itemId);

    void clear(Long userId);

    /** Used by checkout, which needs the managed entity rather than a DTO. */
    Cart requireCartEntity(Long userId);
}
