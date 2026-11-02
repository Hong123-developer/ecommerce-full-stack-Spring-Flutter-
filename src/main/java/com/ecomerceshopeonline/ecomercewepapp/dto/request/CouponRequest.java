package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @Size(max = 200) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
        @DecimalMin("0.01") BigDecimal maximumDiscountAmount,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @Positive Integer usageLimit,
        boolean enabled
) {}
