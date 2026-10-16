package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.Notification;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.NotificationType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One notification. {@code title} / {@code message} are English; the order fields let a
 * client word it in its own language instead.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        PaymentMethod paymentMethod,
        DeliveryType deliveryType,
        BigDecimal amount,
        String actorName,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getOrderId(), n.getOrderNumber(), n.getOrderStatus(), n.getPaymentMethod(),
                n.getDeliveryType(), n.getAmount(), n.getActorName(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
