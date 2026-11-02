package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderTrackingResponse(
        Long orderId,
        String orderNumber,
        OrderStatus currentStatus,
        List<Event> events
) {
    public record Event(OrderStatus status, String note, LocalDateTime createdAt) {
    }
}
