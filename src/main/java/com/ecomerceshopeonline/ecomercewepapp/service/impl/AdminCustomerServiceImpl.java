package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.CustomerResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.AddressMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.AddressRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.AdminCustomerService;
import com.ecomerceshopeonline.ecomercewepapp.service.RefreshTokenService;
import com.ecomerceshopeonline.ecomercewepapp.util.DailySeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCustomerServiceImpl implements AdminCustomerService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, Boolean enabled, Pageable pageable) {
        return PageResponse.from(
                userRepository.adminSearch(RoleName.ROLE_CUSTOMER, blankToNull(search),
                        enabled, pageable),
                this::toSummary);
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

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);
        if (isAdmin) {
            throw new BadRequestException("Administrator accounts cannot be disabled here");
        }

        user.setEnabled(enabled);
        userRepository.save(user);

        if (!enabled) {
            // Disabling must take effect now, not when their access token happens to expire.
            refreshTokenService.revokeAllForUser(customerId);
            log.info("Customer {} disabled and all sessions revoked", customerId);
        }
        return toSummary(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardResponse.DailyCount> registrationHistory(int days) {
        int window = Math.clamp(days, 1, 365);
        return DailySeries.counts(
                userRepository.registrationsByDay(LocalDateTime.now().minusDays(window)));
    }

    private CustomerResponse toSummary(User user) {
        return new CustomerResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getProfileImage(), user.getProvider(), user.isEmailVerified(),
                user.isEnabled(), user.getCreatedAt(),
                null, null, null);
    }

    private User require(Long customerId) {
        return userRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
