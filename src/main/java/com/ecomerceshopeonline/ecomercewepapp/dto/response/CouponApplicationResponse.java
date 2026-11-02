package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;

public record CouponApplicationResponse(String code, BigDecimal discount, BigDecimal subtotal,
                                        BigDecimal totalAfterDiscount) {}
