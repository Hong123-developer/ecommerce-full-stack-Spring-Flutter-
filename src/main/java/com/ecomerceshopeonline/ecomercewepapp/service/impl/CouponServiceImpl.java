package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CouponRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CouponApplicationResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.CouponResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Coupon;
import com.ecomerceshopeonline.ecomercewepapp.entity.CouponUsage;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DiscountType;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.repository.CouponRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CouponUsageRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> list(Pageable pageable) {
        return couponRepository.findAll(pageable).map(this::response);
    }

    @Override
    @Transactional
    public CouponResponse create(CouponRequest request) {
        validateRequest(request);
        if (couponRepository.findByCodeIgnoreCase(normalize(request.code())).isPresent()) {
            throw new ConflictException("Coupon code already exists");
        }
        return response(couponRepository.save(from(request, new Coupon())));
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        validateRequest(request);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        couponRepository.findByCodeIgnoreCase(normalize(request.code())).ifPresent(other -> {
            if (!other.getId().equals(id)) throw new ConflictException("Coupon code already exists");
        });
        return response(couponRepository.save(from(request, coupon)));
    }

    @Override
    @Transactional
    public CouponResponse setEnabled(Long id, boolean enabled) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        coupon.setEnabled(enabled);
        return response(coupon);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        if (coupon.getUsageCount() > 0) throw new ConflictException("Used coupons cannot be deleted");
        couponRepository.delete(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponApplicationResponse preview(Long userId, String code, BigDecimal subtotal) {
        AppliedCoupon applied = calculate(userId, code, subtotal, false);
        return new CouponApplicationResponse(applied.coupon().code(), applied.discount(),
                money(subtotal), money(subtotal.subtract(applied.discount())));
    }

    @Override
    @Transactional
    public AppliedCoupon applyForCheckout(Long userId, String code, BigDecimal subtotal) {
        return calculate(userId, code, subtotal, true);
    }

    @Override
    public void recordUse(AppliedCoupon applied, Order order) {
        if (applied == null) return;
        Coupon coupon = couponRepository.findByIdForUpdate(applied.couponId())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon no longer exists"));
        coupon.setUsageCount(coupon.getUsageCount() + 1);
        User user = userRepository.getReferenceById(order.getUser().getId());
        usageRepository.save(CouponUsage.builder().coupon(coupon).user(user).order(order).build());
    }

    private AppliedCoupon calculate(Long userId, String code, BigDecimal subtotal, boolean lock) {
        if (code == null || code.isBlank()) throw new BadRequestException("Coupon code is required");
        if (subtotal == null || subtotal.signum() < 0) throw new BadRequestException("Invalid order subtotal");
        Coupon coupon = (lock ? couponRepository.findByCodeIgnoreCase(normalize(code))
                : couponRepository.findByCodeIgnoreCase(normalize(code)))
                .orElseThrow(() -> new BadRequestException("Coupon code is invalid"));
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isEnabled() || now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())) {
            throw new BadRequestException("Coupon is not active");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsageCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("Coupon usage limit has been reached");
        }
        if (usageRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new BadRequestException("You have already used this coupon");
        }
        if (subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new BadRequestException("Order does not meet the coupon minimum");
        }
        BigDecimal discount = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)
                : coupon.getDiscountValue();
        if (coupon.getMaximumDiscountAmount() != null
                && discount.compareTo(coupon.getMaximumDiscountAmount()) > 0) {
            discount = coupon.getMaximumDiscountAmount();
        }
        discount = discount.min(subtotal).setScale(2, RoundingMode.DOWN);
        return new AppliedCoupon(response(coupon), discount, coupon.getId());
    }

    private Coupon from(CouponRequest request, Coupon coupon) {
        coupon.setCode(normalize(request.code()));
        coupon.setDescription(clean(request.description()));
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(money(request.discountValue()));
        coupon.setMinimumOrderAmount(money(request.minimumOrderAmount()));
        coupon.setMaximumDiscountAmount(request.maximumDiscountAmount() == null ? null : money(request.maximumDiscountAmount()));
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setEnabled(request.enabled());
        return coupon;
    }

    private void validateRequest(CouponRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) throw new BadRequestException("Coupon end must be after start");
        if (request.discountType() == DiscountType.PERCENTAGE && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Percentage discount cannot exceed 100");
        }
    }

    private CouponResponse response(Coupon c) {
        return new CouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getMinimumOrderAmount(), c.getMaximumDiscountAmount(),
                c.getStartsAt(), c.getEndsAt(), c.getUsageLimit(), c.getUsageCount(), c.isEnabled());
    }

    private static String normalize(String value) { return value.trim().toUpperCase(java.util.Locale.ROOT); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
