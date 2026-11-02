package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CouponCodeRequest(@NotBlank String code) {}
