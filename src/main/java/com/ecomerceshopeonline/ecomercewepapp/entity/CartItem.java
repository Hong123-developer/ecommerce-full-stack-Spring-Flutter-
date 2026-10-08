package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
/*
 * (cart, product, variant) is unique. Because NULLs are distinct in a unique constraint, plain
 * product lines (variant_id NULL) are additionally covered by the partial unique index
 * uk_cart_items_cart_product_plain, created by LegacySchemaCleanup on PostgreSQL.
 */
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_cart_product_variant",
                columnNames = {"cart_id", "product_id", "variant_id"}
        )
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cart_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_items_cart")
    )
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_items_product")
    )
    private Product product;

    /** The chosen option combination; null for a product without variants. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            foreignKey = @ForeignKey(name = "fk_cart_items_variant")
    )
    private ProductVariant variant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The variant's price when one is chosen, otherwise the product's. */
    public BigDecimal getUnitPrice() {
        if (variant != null) {
            return variant.getPrice();
        }
        return product == null ? null : product.getPrice();
    }

    /** Live price x quantity. The cart is not a snapshot - it follows the current catalogue price. */
    public BigDecimal getLineTotal() {
        BigDecimal unitPrice = getUnitPrice();
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
