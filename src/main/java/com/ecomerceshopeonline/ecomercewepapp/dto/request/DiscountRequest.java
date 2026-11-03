package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DiscountRequest(
        @NotNull(message = "Discount is required")
        @Min(value = 0, message = "Discount cannot be negative")
        @Max(value = 100, message = "Discount cannot exceed 100 percent")
        Integer discountPercent,
        LocalDate discountStartDate,
        LocalDate discountEndDate
) {
}
