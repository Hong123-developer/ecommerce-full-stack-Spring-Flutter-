package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The full new display order: every delivery option id exactly once, first option first. */
public record DeliveryOptionOrderRequest(

        @NotNull(message = "Option ids are required")
        List<Long> ids
) {
}
