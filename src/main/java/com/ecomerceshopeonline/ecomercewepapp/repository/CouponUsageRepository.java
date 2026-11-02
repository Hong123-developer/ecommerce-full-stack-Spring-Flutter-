package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
