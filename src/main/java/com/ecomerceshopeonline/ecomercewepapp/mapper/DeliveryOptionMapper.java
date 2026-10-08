package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DeliveryOptionRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DeliveryOptionResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.DeliveryOption;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The request is already trimmed (blank -> null) by its compact constructor. */
@Component
public class DeliveryOptionMapper {

    public DeliveryOptionResponse toResponse(DeliveryOption option) {
        return new DeliveryOptionResponse(
                option.getId(),
                option.getName(),
                option.getDescription(),
                option.getType(),
                option.getFee(),
                option.getEstimatedTime(),
                option.getLogoUrl(),
                option.isEnabled(),
                option.getSortOrder(),
                option.getCreatedAt(),
                option.getUpdatedAt());
    }

    /** {@code sortOrder} is resolved by the caller, which knows where "the end" is. */
    public DeliveryOption toEntity(DeliveryOptionRequest request, int sortOrder) {
        return DeliveryOption.builder()
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .fee(money(request.fee()))
                .estimatedTime(request.estimatedTime())
                .logoUrl(request.logoUrl())
                .sortOrder(sortOrder)
                .enabled(request.enabled() == null || request.enabled())
                .build();
    }

    /** In-place update of a managed entity - id and audit columns are left alone. */
    public void apply(DeliveryOptionRequest request, DeliveryOption target) {
        target.setName(request.name());
        target.setDescription(request.description());
        target.setType(request.type());
        target.setFee(money(request.fee()));
        target.setEstimatedTime(request.estimatedTime());
        target.setLogoUrl(request.logoUrl());
        if (request.sortOrder() != null) {
            target.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        }
    }

    /** Validation already capped it at 2 decimals; this just makes 3 come back as 3.00. */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
