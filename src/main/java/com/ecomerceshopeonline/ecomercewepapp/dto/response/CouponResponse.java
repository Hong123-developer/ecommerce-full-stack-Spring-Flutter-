package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(Long id, String code, String description, DiscountType discountType,
                             BigDecimal discountValue, BigDecimal minimumOrderAmount,
                             BigDecimal maximumDiscountAmount, LocalDateTime startsAt,
                             LocalDateTime endsAt, Integer usageLimit, int usageCount,
                             boolean enabled) {}
