package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderItemResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class OrderMapper {

    /** A shopper may still pull out while the order has not been picked or shipped. */
    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);

    public static boolean isCancellable(OrderStatus status) {
        return CANCELLABLE.contains(status);
    }

    public OrderResponse toResponse(Order order, boolean includeCustomer) {
        Payment payment = order.getPayment();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDeliveryFee(),
                order.getDiscount(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                payment == null ? null : payment.getPaymentMethod(),
                payment == null ? null : payment.getPaymentStatus(),
                payment == null ? null : payment.getId(),
                isCancellable(order.getStatus()),
                includeCustomer ? toCustomer(order.getUser()) : null,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderSummaryResponse toSummary(Order order, boolean includeCustomer) {
        Payment payment = order.getPayment();
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                payment == null ? null : payment.getPaymentMethod(),
                payment == null ? null : payment.getPaymentStatus(),
                isCancellable(order.getStatus()),
                includeCustomer ? toCustomer(order.getUser()) : null,
                order.getCreatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct() == null ? null : item.getProduct().getId(),
                item.getProductName(),
                item.getProduct() == null ? null : item.getProduct().getImageUrl(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }

    private OrderResponse.CustomerSummary toCustomer(User user) {
        return new OrderResponse.CustomerSummary(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
