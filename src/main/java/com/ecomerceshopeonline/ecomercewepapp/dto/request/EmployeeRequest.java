package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 60, message = "First name must be at most 60 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 60, message = "Last name must be at most 60 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email,

        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        @NotBlank(message = "Position is required")
        @Size(max = 80, message = "Position must be at most 80 characters")
        String position,

        @Size(max = 60, message = "Department must be at most 60 characters")
        String department,

        @PositiveOrZero(message = "Salary must be greater than or equal to 0")
        BigDecimal salary,

        LocalDate hireDate,

        EmployeeStatus status,

        @Size(max = 500, message = "Avatar URL must be at most 500 characters")
        String avatarUrl,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address
) {
}
