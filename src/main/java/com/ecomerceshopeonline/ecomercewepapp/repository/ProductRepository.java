package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByNameIgnoreCase(String name);


    @Query(value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            WHERE p.enabled = true
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.enabled = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    Page<Product> browse(@Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         Pageable pageable);

    /** Admin listing - includes disabled products. */
    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category " +
            "WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))",
            countQuery = "SELECT COUNT(p) FROM Product p " +
                    "WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))")
    Page<Product> findAllWithCategory(@Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") Long id);

    // ---------- ratings (written by ProductReviewService only) ----------

    /**
     * Locks the product row before one of its reviews changes, so two concurrent review
     * changes recompute the rating one after the other - the second sees the first's row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * Stores a recomputed rating. A bulk update instead of a dirty entity, so a review does not
     * move the product's updated_at. Not clearAutomatically - see {@link #incrementStock}.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.ratingAverage = :average, p.ratingCount = :count WHERE p.id = :productId")
    int updateRating(@Param("productId") Long productId,
                     @Param("average") BigDecimal average,
                     @Param("count") int count);

    // ---------- display order (admin move up / move down) ----------

    /** Rows of [id, sortOrder] for every product, in display order - first product first. */
    @Query("SELECT p.id, p.sortOrder FROM Product p ORDER BY p.sortOrder ASC, p.id DESC")
    List<Object[]> findDisplayOrder();

    /** The top slot's value, so a new product can go above it; 0 for an empty catalogue. */
    @Query("SELECT COALESCE(MIN(p.sortOrder), 0) FROM Product p")
    int findMinSortOrder();

    /** Not clearAutomatically - callers load the moved product only after their updates. */
    @Modifying
    @Query("UPDATE Product p SET p.sortOrder = :sortOrder WHERE p.id = :id")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

    /**
     * Conditional stock decrement. Returns 0 when there was not enough stock, which lets the
     * caller reject the checkout without a read-then-write race between concurrent orders.
     */
    @Modifying
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = p.stockQuantity - :quantity
             WHERE p.id = :productId
               AND p.stockQuantity >= :quantity
            """)
    int decrementStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Puts stock back when an order is cancelled.
     * Deliberately NOT {@code clearAutomatically} - callers hold a managed Order they are
     * about to mutate, and clearing the persistence context would detach it mid-transaction.
     */
    @Modifying
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = p.stockQuantity + :quantity
             WHERE p.id = :productId
            """)
    int incrementStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Puts a plain order line's units back - only while the product is still plain. A product
     * that gained enabled variants after the order derives its stock from them, so adding the
     * units here would push the total above the sum of its variants.
     * Not clearAutomatically - see {@link #incrementStock}.
     */
    @Modifying
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = p.stockQuantity + :quantity
             WHERE p.id = :productId
               AND NOT EXISTS (SELECT v.id FROM ProductVariant v
                                WHERE v.product.id = :productId AND v.enabled = true)
            """)
    int incrementStockIfPlain(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Relative change of a variant product's total after one of its variants' stock moved by
     * {@code delta} in the same transaction (checkout: negative, cancellation: positive).
     * Applied only while that variant is enabled - a retired variant no longer counts towards
     * the product total, and a product whose variants are all retired is a plain product whose
     * stock the admin owns. Relative (not a re-computed SUM) so concurrent orders on different
     * variants of the same product cannot overwrite each other's change under READ COMMITTED.
     */
    @Modifying
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = p.stockQuantity + :delta
             WHERE p.id = :productId
               AND EXISTS (SELECT v.id FROM ProductVariant v
                            WHERE v.id = :variantId AND v.product.id = :productId AND v.enabled = true)
            """)
    int adjustStockForVariant(@Param("productId") Long productId,
                              @Param("variantId") Long variantId,
                              @Param("delta") int delta);

    /**
     * Re-derives a variant product's stock as the sum of its enabled variants (absolute
     * recompute - for admin-side use only, never on the checkout/cancel path). A product
     * without any enabled variant is a plain product and is left alone.
     */
    @Modifying
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = (SELECT CAST(COALESCE(SUM(v.stockQuantity), 0) AS Integer)
                                        FROM ProductVariant v
                                       WHERE v.product.id = :productId AND v.enabled = true)
             WHERE p.id = :productId
               AND EXISTS (SELECT v2.id FROM ProductVariant v2
                            WHERE v2.product.id = :productId AND v2.enabled = true)
            """)
    int syncStockFromVariants(@Param("productId") Long productId);

    long countByEnabledTrue();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.enabled = true AND p.stockQuantity <= :threshold")
    long countLowStock(@Param("threshold") int threshold);
}
