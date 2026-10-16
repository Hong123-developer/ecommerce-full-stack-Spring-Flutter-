package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    // ---------- storefront ----------

    /** Visible reviews of one product, optionally only one star value. The author is join-fetched for the name. */
    @Query(value = """
            SELECT r FROM ProductReview r
            JOIN FETCH r.user u
            WHERE r.product.id = :productId
              AND r.visible = true
              AND (:rating IS NULL OR r.rating = :rating)
            """,
            countQuery = """
            SELECT COUNT(r) FROM ProductReview r
            WHERE r.product.id = :productId
              AND r.visible = true
              AND (:rating IS NULL OR r.rating = :rating)
            """)
    Page<ProductReview> findVisible(@Param("productId") Long productId,
                                    @Param("rating") Integer rating,
                                    Pageable pageable);

    /** Rows of [rating, count] over the visible reviews of one product - the star distribution. */
    @Query("""
            SELECT r.rating, COUNT(r) FROM ProductReview r
            WHERE r.product.id = :productId AND r.visible = true
            GROUP BY r.rating
            """)
    List<Object[]> countVisibleByRating(@Param("productId") Long productId);

    @Query("""
            SELECT r FROM ProductReview r
            JOIN FETCH r.user
            WHERE r.product.id = :productId AND r.user.id = :userId
            """)
    Optional<ProductReview> findByProductIdAndUserId(@Param("productId") Long productId,
                                                     @Param("userId") Long userId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @Query("SELECT r FROM ProductReview r JOIN FETCH r.user JOIN FETCH r.product WHERE r.id = :id")
    Optional<ProductReview> findDetailById(@Param("id") Long id);

    /** The product a review belongs to (it never changes), so its row can be locked first. */
    @Query("SELECT r.product.id FROM ProductReview r WHERE r.id = :id")
    Optional<Long> findProductIdById(@Param("id") Long id);

    // ---------- admin ----------

    /** Moderation list. Every filter is optional; search matches the product name or the author's name / email. */
    @Query(value = """
            SELECT r FROM ProductReview r
            JOIN FETCH r.product p
            JOIN FETCH r.user u
            WHERE (:rating IS NULL OR r.rating = :rating)
              AND (:visible IS NULL OR r.visible = :visible)
              AND (:search IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """,
            countQuery = """
            SELECT COUNT(r) FROM ProductReview r
            JOIN r.product p
            JOIN r.user u
            WHERE (:rating IS NULL OR r.rating = :rating)
              AND (:visible IS NULL OR r.visible = :visible)
              AND (:search IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<ProductReview> adminSearch(@Param("search") String search,
                                    @Param("rating") Integer rating,
                                    @Param("visible") Boolean visible,
                                    Pageable pageable);

    /**
     * Hide / show. A bulk update so the author's updated_at (shown as "edited") stays theirs.
     * clearAutomatically: the caller reads the review again afterwards and must see the new value.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductReview r SET r.visible = :visible WHERE r.id = :id")
    int updateVisible(@Param("id") Long id, @Param("visible") boolean visible);

    // ---------- housekeeping ----------

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProductReview r WHERE r.id = :id")
    int bulkDeleteById(@Param("id") Long id);

    /** Product deletion - its reviews go with it. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProductReview r WHERE r.product.id = :productId")
    int bulkDeleteByProductId(@Param("productId") Long productId);

    @Query("SELECT DISTINCT r.product.id FROM ProductReview r WHERE r.user.id = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    /** Account deletion - removes the user's reviews (the caller recomputes the products' ratings). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProductReview r WHERE r.user.id = :userId")
    int bulkDeleteByUserId(@Param("userId") Long userId);

    // ---------- dashboard ----------

    long countByVisible(boolean visible);

    long countByCreatedAtAfter(LocalDateTime since);

    /** Average stars over every visible review in the shop; null without any. */
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.visible = true")
    Double averageVisibleRating();
}
