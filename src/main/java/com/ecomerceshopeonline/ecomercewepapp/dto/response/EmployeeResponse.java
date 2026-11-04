package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String position,
        String department,
        BigDecimal salary,
        LocalDate hireDate,
        EmployeeStatus status,
        String avatarUrl,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
