package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The full new carousel order: every slide id exactly once, first slide first. */
public record HeroSlideOrderRequest(

        @NotNull(message = "Slide ids are required")
        List<Long> ids
) {
}
