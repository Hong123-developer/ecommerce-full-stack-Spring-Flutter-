package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(

        @NotNull(message = "Status is required")
        OrderStatus status
) {
}
