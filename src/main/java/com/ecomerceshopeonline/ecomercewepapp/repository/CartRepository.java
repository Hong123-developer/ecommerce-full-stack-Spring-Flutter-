package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /**
     * Loads the cart with its lines and each line's product in one query - rendering a cart
     * touches every product, so lazy loading here would be a guaranteed N+1. The chosen
     * variant is fetched too; its option values are batch-loaded.
     */
    @Query("""
            SELECT DISTINCT c FROM Cart c
            LEFT JOIN FETCH c.items i
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH i.variant v
            WHERE c.user.id = :userId
            """)
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);

    /** Account deletion - the cart's lines must be removed first (see CartItemRepository). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Cart c WHERE c.user.id = :userId")
    int bulkDeleteByUserId(@Param("userId") Long userId);
}
