package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductReviewRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.StatisticsService;
import com.ecomerceshopeonline.ecomercewepapp.util.DailySeries;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TOP_PRODUCT_LIMIT = 5;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(int days) {
        int window = Math.clamp(days, 1, 365);
        LocalDateTime since = LocalDateTime.now().minusDays(window);

        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        long totalOrders = 0;
        for (OrderStatus status : OrderStatus.values()) {
            long count = orderRepository.countByStatus(status);
            ordersByStatus.put(status, count);
            totalOrders += count;
        }

        // Revenue is counted from settled payments, not from order totals - an order that
        // was placed but never paid is not money.
        BigDecimal totalRevenue = paymentRepository.sumAmountByStatus(PaymentStatus.PAID);
        BigDecimal recentRevenue = paymentRepository.sumAmountSince(PaymentStatus.PAID, since);

        List<DashboardResponse.TopProduct> topProducts = orderRepository
                .topSellingProducts(OrderStatus.CANCELLED, PageRequest.of(0, TOP_PRODUCT_LIMIT))
                .stream()
                .map(row -> new DashboardResponse.TopProduct(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]))
                .toList();

        return new DashboardResponse(
                new DashboardResponse.Revenue(
                        totalRevenue,
                        recentRevenue,
                        paymentRepository.countByPaymentStatus(PaymentStatus.PAID),
                        paymentRepository.countByPaymentStatus(PaymentStatus.PENDING)),
                new DashboardResponse.Orders(
                        totalOrders,
                        ordersByStatus,
                        orderRepository.totalOrderValueExcluding(OrderStatus.CANCELLED)),
                new DashboardResponse.Customers(
                        userRepository.countByRole(RoleName.ROLE_CUSTOMER),
                        userRepository.countByRoleCreatedSince(RoleName.ROLE_CUSTOMER, since)),
                new DashboardResponse.Catalogue(
                        productRepository.countByEnabledTrue(),
                        productRepository.countLowStock(LOW_STOCK_THRESHOLD)),
                topProducts,
                DailySeries.points(paymentRepository.revenueByDay(PaymentStatus.PAID, since)),
                DailySeries.counts(userRepository.registrationsByDay(RoleName.ROLE_CUSTOMER, since)),
                reviews(since));
    }

    private DashboardResponse.Reviews reviews(LocalDateTime since) {
        Double average = reviewRepository.averageVisibleRating();
        long hidden = reviewRepository.countByVisible(false);
        return new DashboardResponse.Reviews(
                average == null
                        ? BigDecimal.ZERO.setScale(1)
                        : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP),
                reviewRepository.countByVisible(true) + hidden,
                hidden,
                reviewRepository.countByCreatedAtAfter(since));
    }
}
