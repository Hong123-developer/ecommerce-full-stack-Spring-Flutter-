package com.ecomerceshopeonline.ecomercewepapp.dto.response;

public record EmployeeStatsResponse(
        long total,
        long active,
        long onLeave,
        long inactive,
        long terminated
) {
}
