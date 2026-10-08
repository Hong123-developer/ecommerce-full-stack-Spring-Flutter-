package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String profileImage,
        AuthProvider provider,
        boolean emailVerified,
        boolean enabled,
        LocalDateTime registeredAt,
        /** List and detail views. */
        Long orderCount,
        /** Detail view only. */
        BigDecimal totalSpent,
        List<AddressResponse> addresses
) {
}
