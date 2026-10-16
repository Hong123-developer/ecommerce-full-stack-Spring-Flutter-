package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Guards hard-deletion of a product that already appears in order history. */
    boolean existsByProductId(Long productId);

    /** A variant that was ordered is disabled instead of deleted. */
    boolean existsByVariantId(Long variantId);

    /** Whether the customer has an order in one of these states with a line of this product (review eligibility). */
    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
            FROM OrderItem oi JOIN oi.order o
            WHERE o.user.id = :userId AND oi.product.id = :productId AND o.status IN :statuses
            """)
    boolean existsPurchase(@Param("userId") Long userId,
                           @Param("productId") Long productId,
                           @Param("statuses") Collection<OrderStatus> statuses);

    /** The customer's lines of this product in orders of one state, latest order first. */
    @Query("""
            SELECT oi FROM OrderItem oi JOIN oi.order o
            WHERE o.user.id = :userId AND oi.product.id = :productId AND o.status = :status
            ORDER BY o.createdAt DESC, oi.id DESC
            """)
    List<OrderItem> findPurchasedLines(@Param("userId") Long userId,
                                       @Param("productId") Long productId,
                                       @Param("status") OrderStatus status,
                                       Pageable pageable);
}
