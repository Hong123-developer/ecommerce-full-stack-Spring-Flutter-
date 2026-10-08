package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    void deleteByCartId(Long cartId);

    /** Used when a product is removed from the catalogue - carts must not hold dangling rows. */
    void deleteByProductId(Long productId);

    /** Lines pointing at variants that were removed or disabled. */
    void deleteByVariantIdIn(Collection<Long> variantIds);

    /** Lines added before the product had variants - they no longer say which option to ship. */
    void deleteByProductIdAndVariantIsNull(Long productId);

    /** Account deletion - every line of every cart the user owns. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM CartItem i WHERE i.cart.id IN (SELECT c.id FROM Cart c WHERE c.user.id = :userId)")
    int bulkDeleteByUserId(@Param("userId") Long userId);
}
