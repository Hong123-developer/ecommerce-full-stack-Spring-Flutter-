package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_name", columnList = "name")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private Category category;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Extra photos shown after {@link #imageUrl} on the product page, in display order.
     * {@code imageUrl} stays the main image everywhere else (cards, cart, orders).
     * Batch-fetched so a page of products costs one extra query, not one per product.
     */
    @ElementCollection
    @CollectionTable(
            name = "product_gallery_images",
            joinColumns = @JoinColumn(name = "product_id",
                    foreignKey = @ForeignKey(name = "fk_product_gallery_product")))
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500, nullable = false)
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> galleryUrls = new ArrayList<>();

    /**
     * Names of the choices a shopper makes, e.g. [Model, Storage, Color]. Empty for a plain
     * product. Each variant's option values line up with these by position.
     */
    @ElementCollection
    @CollectionTable(
            name = "product_option_names",
            joinColumns = @JoinColumn(name = "product_id",
                    foreignKey = @ForeignKey(name = "fk_product_option_names_product")))
    @OrderColumn(name = "position")
    @Column(name = "name", length = 30, nullable = false)
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> optionNames = new ArrayList<>();

    /**
     * Buyable combinations of {@link #optionNames}. When there is at least one, {@link #price}
     * and {@link #stockQuantity} are derived from them (see ProductVariantSync) so catalogue
     * filters and sorts keep working on the product row. Not orphan-removed: the service
     * decides whether a dropped variant is deleted or only disabled.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("position ASC, id ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    public boolean hasEnabledVariants() {
        return variants.stream().anyMatch(ProductVariant::isEnabled);
    }

    public boolean isInStock(int requested) {
        return stockQuantity != null && stockQuantity >= requested;
    }
}
