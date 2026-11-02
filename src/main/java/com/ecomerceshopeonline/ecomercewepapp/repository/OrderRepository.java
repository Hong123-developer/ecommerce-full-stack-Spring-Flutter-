package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNumber(String orderNumber);

    // ---------- customer ----------

    @Query(value = "SELECT o FROM Order o LEFT JOIN FETCH o.payment WHERE o.user.id = :userId",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Page<Order> findByUserWithPayment(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.variant
            LEFT JOIN FETCH o.payment
            WHERE o.id = :id AND o.user.id = :userId
            """)
    Optional<Order> findDetailByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // ---------- admin ----------

    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.variant
            LEFT JOIN FETCH o.payment
            LEFT JOIN FETCH o.user
            WHERE o.id = :id
            """)
    Optional<Order> findDetailById(@Param("id") Long id);

    @Query(value = """
            SELECT o FROM Order o
            LEFT JOIN FETCH o.payment
            LEFT JOIN FETCH o.user u
            WHERE (:status IS NULL OR o.status = :status)
              AND (:search IS NULL
                   OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """,
            countQuery = """
            SELECT COUNT(o) FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:search IS NULL
                   OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(o.user.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<Order> adminSearch(@Param("status") OrderStatus status,
                            @Param("search") String search,
                            Pageable pageable);

    // ---------- statistics ----------

    long countByStatus(OrderStatus status);

    long countByUserId(Long userId);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.user.id = :userId AND o.status <> :excluded
            """)
    BigDecimal totalSpentByUser(@Param("userId") Long userId,
                                @Param("excluded") OrderStatus excluded);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.status <> :excluded
            """)
    BigDecimal totalOrderValueExcluding(@Param("excluded") OrderStatus excluded);

    /** Rows of [productName, unitsSold, revenue], best sellers first. */
    @Query("""
            SELECT oi.productName, SUM(oi.quantity), SUM(oi.subtotal)
            FROM OrderItem oi
            WHERE oi.order.status <> :excluded
            GROUP BY oi.productName
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> topSellingProducts(@Param("excluded") OrderStatus excluded, Pageable pageable);

    /** Rows of [userId, orderCount] for a page of customers - one query instead of N. */
    @Query("SELECT o.user.id, COUNT(o) FROM Order o WHERE o.user.id IN :userIds GROUP BY o.user.id")
    List<Object[]> countByUserIds(@Param("userIds") Collection<Long> userIds);
}
