package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdWithOrder(@Param("orderId") Long orderId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.id = :id AND p.user.id = :userId")
    Optional<Payment> findByIdAndUserIdWithOrder(@Param("id") Long id,
                                                 @Param("userId") Long userId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.order JOIN FETCH p.user WHERE p.id = :id")
    Optional<Payment> findDetailById(@Param("id") Long id);

    // ---------- card payments ----------

    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.gatewayTranId = :tranId")
    Optional<Payment> findByGatewayTranIdWithOrder(@Param("tranId") String tranId);

    /** Unpaid payments of untouched orders whose latest activity is older than the cutoff. */
    @Query("""
            SELECT p.id FROM Payment p
            WHERE p.paymentMethod = :method
              AND p.paymentStatus IN :statuses
              AND p.order.status = :orderStatus
              AND p.updatedAt < :cutoff
            """)
    List<Long> findStaleIds(@Param("method") PaymentMethod method,
                            @Param("statuses") Collection<PaymentStatus> statuses,
                            @Param("orderStatus") OrderStatus orderStatus,
                            @Param("cutoff") LocalDateTime cutoff);

    @Query(value = "SELECT p FROM Payment p JOIN FETCH p.order WHERE p.user.id = :userId",
            countQuery = "SELECT COUNT(p) FROM Payment p WHERE p.user.id = :userId")
    Page<Payment> findByUserWithOrder(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT p FROM Payment p
            JOIN FETCH p.order o
            JOIN FETCH p.user u
            WHERE (:status IS NULL OR p.paymentStatus = :status)
              AND (:search IS NULL
                   OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(p.transactionId) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Payment p
            WHERE (:status IS NULL OR p.paymentStatus = :status)
              AND (:search IS NULL
                   OR LOWER(p.order.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(p.user.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(p.transactionId) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<Payment> adminSearch(@Param("status") PaymentStatus status,
                              @Param("search") String search,
                              Pageable pageable);

    // ---------- statistics ----------

    long countByPaymentStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentStatus = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.paymentStatus = :status AND p.paidAt >= :since
            """)
    BigDecimal sumAmountSince(@Param("status") PaymentStatus status,
                              @Param("since") LocalDateTime since);

    /** Rows of [day, revenue] for a simple sales chart. */
    @Query("""
            SELECT CAST(p.paidAt AS date), COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.paymentStatus = :status AND p.paidAt >= :since
            GROUP BY CAST(p.paidAt AS date)
            ORDER BY CAST(p.paidAt AS date)
            """)
    List<Object[]> revenueByDay(@Param("status") PaymentStatus status,
                                @Param("since") LocalDateTime since);

    /** Account deletion - only ever reached for users without orders, so normally a no-op. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Payment p WHERE p.user.id = :userId")
    int bulkDeleteByUserId(@Param("userId") Long userId);
}
