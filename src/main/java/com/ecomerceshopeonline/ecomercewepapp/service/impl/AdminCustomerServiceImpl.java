package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.CustomerResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.AddressMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.AddressRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.NotificationRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RefreshTokenRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.VerificationCodeRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.AdminCustomerService;
import com.ecomerceshopeonline.ecomercewepapp.service.ProductReviewService;
import com.ecomerceshopeonline.ecomercewepapp.service.RefreshTokenService;
import com.ecomerceshopeonline.ecomercewepapp.util.DailySeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCustomerServiceImpl implements AdminCustomerService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final RefreshTokenService refreshTokenService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final NotificationRepository notificationRepository;
    private final ProductReviewService productReviewService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, Boolean enabled, Pageable pageable) {
        Page<User> page = userRepository.adminSearch(RoleName.ROLE_CUSTOMER, blankToNull(search),
                enabled, pageable);
        Map<Long, Long> orderCounts = orderCountsFor(page.getContent());
        return PageResponse.from(page,
                user -> toSummary(user, orderCounts.getOrDefault(user.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(Long customerId) {
        User user = require(customerId);
        return new CustomerResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getProfileImage(), user.getProvider(), user.isEmailVerified(),
                user.isEnabled(), user.getCreatedAt(),
                orderRepository.countByUserId(customerId),
                orderRepository.totalSpentByUser(customerId, OrderStatus.CANCELLED),
                addressRepository.findByUserId(customerId).stream()
                        .map(addressMapper::toResponse).toList());
    }

    @Override
    @Transactional
    public CustomerResponse setEnabled(Long customerId, boolean enabled) {
        User user = require(customerId);

        if (isAdmin(user)) {
            throw new BadRequestException("Administrator accounts cannot be disabled here");
        }

        user.setEnabled(enabled);
        userRepository.save(user);

        if (!enabled) {
            // Disabling must take effect now, not when their access token happens to expire.
            refreshTokenService.revokeAllForUser(customerId);
            log.info("Customer {} disabled and all sessions revoked", customerId);
        }
        return toSummary(user, orderRepository.countByUserId(customerId));
    }

    @Override
    @Transactional
    public void delete(Long customerId, Long actingAdminId) {
        User user = require(customerId);

        if (Objects.equals(customerId, actingAdminId)) {
            throw new BadRequestException("You can't delete your own account");
        }
        if (isAdmin(user)) {
            throw new BadRequestException("Administrator accounts can't be deleted here");
        }
        long orders = orderRepository.countByUserId(customerId);
        if (orders > 0) {
            // Orders and payments are financial records - they are never deleted.
            throw new ConflictException(("This customer has %d order(s). Disable the account "
                    + "instead to keep your sales history.").formatted(orders));
        }

        // Children first: every FK to users is NO ACTION, and cart_items references carts.
        verificationCodeRepository.bulkDeleteByUserId(customerId);
        notificationRepository.bulkDeleteByUserId(customerId);
        refreshTokenRepository.bulkDeleteByUserId(customerId);
        cartItemRepository.bulkDeleteByUserId(customerId);
        cartRepository.bulkDeleteByUserId(customerId);
        addressRepository.bulkDeleteByUserId(customerId);
        paymentRepository.bulkDeleteByUserId(customerId);
        // Only possible after their orders were wiped in Settings > Data; recomputes those products' ratings.
        productReviewService.deleteAllByUser(customerId);
        userRepository.deleteRoleLinks(customerId);
        userRepository.bulkDeleteById(customerId);

        // A still-valid access token now resolves to no account, so the JWT filter leaves
        // the request anonymous (401) - see JwtAuthenticationFilter.
        log.info("Customer {} deleted by admin {}", customerId, actingAdminId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardResponse.DailyCount> registrationHistory(int days) {
        int window = Math.clamp(days, 1, 365);
        return DailySeries.counts(
                userRepository.registrationsByDay(RoleName.ROLE_CUSTOMER, LocalDateTime.now().minusDays(window)));
    }

    private CustomerResponse toSummary(User user, long orderCount) {
        return new CustomerResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getProfileImage(), user.getProvider(), user.isEmailVerified(),
                user.isEnabled(), user.getCreatedAt(),
                orderCount, null, null);
    }

    /** Order counts for a page of users in one grouped query; users with none are absent. */
    private Map<Long, Long> orderCountsFor(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : orderRepository.countByUserIds(users.stream().map(User::getId).toList())) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);
    }

    private User require(Long customerId) {
        return userRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
