package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Revenue revenue,
        Orders orders,
        Customers customers,
        Catalogue catalogue,
        List<TopProduct> topProducts,
        /** Daily series for the charts, oldest first. */
        List<DailyPoint> revenueByDay,
        List<DailyCount> registrationsByDay,
        Reviews reviews
) {

    /**
     * @param averageRating average stars over every visible review in the shop, one decimal; 0 without any
     * @param total         every review, hidden ones included
     * @param hidden        reviews an admin hid
     * @param newInPeriod   reviews written in the dashboard's window ({@code days})
     */
    public record Reviews(BigDecimal averageRating, long total, long hidden, long newInPeriod) {
    }

    public record Revenue(
            BigDecimal total,
            BigDecimal last30Days,
            long paidCount,
            long pendingCount
    ) {
    }

    public record Orders(
            long total,
            Map<OrderStatus, Long> byStatus,
            BigDecimal grossValue
    ) {
    }

    public record Customers(long total, long newLast30Days) {
    }

    public record Catalogue(long activeProducts, long lowStockProducts) {
    }

    public record TopProduct(String productName, long unitsSold, BigDecimal revenue) {
    }

    public record DailyPoint(LocalDate day, BigDecimal value) {
    }

    public record DailyCount(LocalDate day, long count) {
    }
}
