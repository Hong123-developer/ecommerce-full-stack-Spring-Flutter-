package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.ProductLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    /** Total likes on a product (all users). */
    long countByProductId(Long productId);

    /**
     * IDs of the products liked by the given user, limited to the supplied product-id set.
     * Used to annotate a catalogue page: one query for the whole page of products.
     */
    @Query("SELECT pl.product.id FROM ProductLike pl WHERE pl.user.id = :userId AND pl.product.id IN :productIds")
    Set<Long> findLikedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    /** All likes of a user, newest first — the "My Wishlist" page. */
    @Query(value = "SELECT pl FROM ProductLike pl JOIN FETCH pl.product p JOIN FETCH p.category WHERE pl.user.id = :userId",
           countQuery = "SELECT COUNT(pl) FROM ProductLike pl WHERE pl.user.id = :userId")
    Page<ProductLike> findByUserIdWithProduct(@Param("userId") Long userId, Pageable pageable);
}
