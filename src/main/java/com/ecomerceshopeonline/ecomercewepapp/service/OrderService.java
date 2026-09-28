package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CheckoutRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /** Turns the signed-in shopper's cart into an order, reserving stock as it goes. */
    OrderResponse checkout(Long userId, CheckoutRequest request);

    PageResponse<OrderSummaryResponse> listOwn(Long userId, Pageable pageable);

    OrderResponse getOwn(Long userId, Long orderId);

    OrderResponse cancelOwn(Long userId, Long orderId);

    // --- admin ---

    PageResponse<OrderSummaryResponse> adminList(OrderStatus status, String search, Pageable pageable);

    OrderResponse adminGet(Long orderId);

    OrderResponse adminUpdateStatus(Long orderId, OrderStatus status);
}
