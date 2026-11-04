package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderItemResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
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

    /**
     * Whether the shopper may cancel it themselves: the status allows it and no money has
     * been taken yet. A paid order needs a refund, which goes through the shop.
     */
    public static boolean isSelfCancellable(Order order) {
        Payment payment = order.getPayment();
        boolean paid = payment != null && payment.getPaymentStatus() == PaymentStatus.PAID;
        return isCancellable(order.getStatus()) && !paid;
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
                order.getCouponCode(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                // getId() on the lazy reference reads the foreign key - no extra query.
                order.getDeliveryOption() == null ? null : order.getDeliveryOption().getId(),
                order.getDeliveryMethodName(),
                order.getDeliveryType(),
                order.getContactName(),
                order.getContactPhone(),
                order.getCustomerNote(),
                order.getLatitude(),
                order.getLongitude(),
                order.getTrackingNumber(),
                order.getCarrierName(),
                order.getCourierName(),
                order.getCourierPhone(),
                order.getEstimatedDeliveryAt(),
                order.getShipmentStage(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                payment == null ? null : payment.getPaymentMethod(),
                payment == null ? null : payment.getPaymentStatus(),
                payment == null ? null : payment.getId(),
                isSelfCancellable(order),
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
                order.getDeliveryType(),
                order.getDeliveryMethodName(),
                payment == null ? null : payment.getPaymentMethod(),
                payment == null ? null : payment.getPaymentStatus(),
                isSelfCancellable(order),
                includeCustomer ? toCustomer(order.getUser()) : null,
                order.getCreatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        ProductVariant variant = item.getVariant();
        String imageUrl = variant != null && variant.getImageUrl() != null
                ? variant.getImageUrl()
                : item.getProduct() == null ? null : item.getProduct().getImageUrl();
        return new OrderItemResponse(
                item.getId(),
                item.getProduct() == null ? null : item.getProduct().getId(),
                item.getProductName(),
                variant == null ? null : variant.getId(),
                item.getVariantLabel(),
                item.getSku(),
                imageUrl,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }

    private OrderResponse.CustomerSummary toCustomer(User user) {
        return new OrderResponse.CustomerSummary(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
