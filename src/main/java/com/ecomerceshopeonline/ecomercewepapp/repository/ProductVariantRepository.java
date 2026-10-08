package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Variants already holding any of these codes. Codes are unique across every product and
     * compared case-insensitively; pass the codes lower-cased.
     */
    @Query("SELECT v FROM ProductVariant v WHERE LOWER(v.sku) IN :skus")
    List<ProductVariant> findBySkuLowerIn(@Param("skus") Collection<String> lowerCaseSkus);

    /**
     * Conditional stock decrement, same pattern as {@link ProductRepository#decrementStock}:
     * returns 0 when there was not enough stock (or the variant was disabled meanwhile).
     */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
               SET v.stockQuantity = v.stockQuantity - :quantity
             WHERE v.id = :variantId
               AND v.enabled = true
               AND v.stockQuantity >= :quantity
            """)
    int decrementStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    /** Puts stock back when an order is cancelled. Not clearAutomatically - see ProductRepository. */
    @Modifying
    @Query("""
            UPDATE ProductVariant v
               SET v.stockQuantity = v.stockQuantity + :quantity
             WHERE v.id = :variantId
            """)
    int incrementStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);
}
