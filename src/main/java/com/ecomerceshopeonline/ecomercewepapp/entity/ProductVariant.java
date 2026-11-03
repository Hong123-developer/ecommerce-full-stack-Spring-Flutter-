package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_variants_sku", columnNames = "sku"),
        indexes = @Index(name = "idx_product_variants_product", columnList = "product_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ProductVariant extends BaseEntity {

    /** Shown between option values in labels: "iPhone 14 Pro · 256GB · Purple". */
    public static final String LABEL_SEPARATOR = " · ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_variants_product")
    )
    private Product product;

    /**
     * Option values in the same order as the product's option names.
     * Batch-fetched so rendering a page of variants is one extra query, not one per variant.
     * The column is {@code option_value} because {@code value} is a reserved word in H2.
     */
    @ElementCollection
    @CollectionTable(
            name = "product_variant_options",
            joinColumns = @JoinColumn(name = "variant_id",
                    foreignKey = @ForeignKey(name = "fk_product_variant_options_variant")))
    @OrderColumn(name = "position")
    @Column(name = "option_value", length = 60, nullable = false)
    @BatchSize(size = 100)
    @Builder.Default
    private List<String> options = new ArrayList<>();

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    /** Optional photo for this variant; the product's main image is used when null. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean enabled = true;

    /** Display order inside the product. */
    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    public String getLabel() {
        return String.join(LABEL_SEPARATOR, options);
    }

    public boolean isInStock(int requested) {
        return stockQuantity != null && stockQuantity >= requested;
    }
}
