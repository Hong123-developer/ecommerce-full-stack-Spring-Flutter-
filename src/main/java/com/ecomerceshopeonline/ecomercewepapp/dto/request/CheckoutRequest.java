package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(

        @NotNull(message = "Delivery address is required")
        Long addressId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod
) {
}
