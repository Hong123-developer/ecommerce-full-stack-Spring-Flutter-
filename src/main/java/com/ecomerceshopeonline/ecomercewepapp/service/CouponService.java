package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CouponRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CouponApplicationResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CouponResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CouponService {
    Page<CouponResponse> list(Pageable pageable);
    CouponResponse create(CouponRequest request);
    CouponResponse update(Long id, CouponRequest request);
    CouponResponse setEnabled(Long id, boolean enabled);
    void delete(Long id);
    CouponApplicationResponse preview(Long userId, String code, BigDecimal subtotal);
    AppliedCoupon applyForCheckout(Long userId, String code, BigDecimal subtotal);
    void recordUse(AppliedCoupon applied, Order order);

    record AppliedCoupon(CouponResponse coupon, BigDecimal discount, Long couponId) {}
}
