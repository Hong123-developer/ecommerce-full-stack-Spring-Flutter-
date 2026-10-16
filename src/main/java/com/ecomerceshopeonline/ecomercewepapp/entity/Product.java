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
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_name", columnList = "name"),
                @Index(name = "idx_products_sort_order", columnList = "sort_order, id")
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

    /**
     * Display position set from the admin Products page (move up / move down): lower comes first.
     * Equal values fall back to the newest product, which is how every product created before
     * ordering existed starts out (all 0). New products take the top slot.
     */
    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int sortOrder = 0;

    /**
     * Average stars of the visible reviews, one decimal (0 without any). Derived - only
     * ProductReviewService writes it, in the same transaction as the review change. Existing
     * rows get 0 from the column default when the column is added.
     */
    @Column(name = "rating_average", nullable = false, precision = 2, scale = 1)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    /** Number of visible reviews. Derived, like {@link #ratingAverage}. */
    @Column(name = "rating_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int ratingCount = 0;

    /**
     * The shop's own star score (0.5 to 5 in half stars), set by the admin and shown to shoppers
     * labelled "Shop rating" - never mixed into the customer {@link #ratingAverage}. Null = none.
     */
    @Column(name = "shop_rating", precision = 2, scale = 1)
    private BigDecimal shopRating;

    /** Optional note shown next to {@link #shopRating}, e.g. "Rated on our Facebook page". */
    @Column(name = "shop_rating_label", length = 60)
    private String shopRatingLabel;

    public boolean hasEnabledVariants() {
        return variants.stream().anyMatch(ProductVariant::isEnabled);
    }

    public boolean isInStock(int requested) {
        return stockQuantity != null && stockQuantity >= requested;
    }
}
