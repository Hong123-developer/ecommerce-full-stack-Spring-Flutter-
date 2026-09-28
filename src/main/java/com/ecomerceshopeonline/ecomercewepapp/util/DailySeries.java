package com.ecomerceshopeonline.ecomercewepapp.util;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Turns the {@code Object[]} rows that JPQL group-by queries return into typed points.
 * The date column comes back as {@link java.sql.Date} on some drivers and
 * {@link LocalDate} on others, so both are handled.
 */
public final class DailySeries {

    private DailySeries() {
    }

    public static List<DashboardResponse.DailyPoint> points(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardResponse.DailyPoint(toLocalDate(row[0]), toDecimal(row[1])))
                .toList();
    }

    public static List<DashboardResponse.DailyCount> counts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardResponse.DailyCount(
                        toLocalDate(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    private static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case LocalDate date -> date;
            case Date date -> date.toLocalDate();
            case java.time.LocalDateTime dateTime -> dateTime.toLocalDate();
            case null -> null;
            default -> LocalDate.parse(value.toString());
        };
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal
                ? decimal
                : BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
