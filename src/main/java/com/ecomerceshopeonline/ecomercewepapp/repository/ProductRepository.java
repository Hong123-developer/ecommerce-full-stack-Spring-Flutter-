package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Public catalogue search. Every filter is optional - a null argument disables that clause.
     * The category is join-fetched because every row is mapped with its category name, which
     * would otherwise be one extra query per product.
     */
    @Query(value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            WHERE p.enabled = true
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.enabled = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    Page<Product> browse(@Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         Pageable pageable);

    /** Admin listing - includes disabled products. */
    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category",
            countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllWithCategory(Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") Long id);

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

    long countByEnabledTrue();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.enabled = true AND p.stockQuantity <= :threshold")
    long countLowStock(@Param("threshold") int threshold);
}
