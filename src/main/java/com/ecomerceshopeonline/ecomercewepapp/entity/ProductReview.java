package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

/**
 * A shopper's star rating (and optional words) for a product they received. One per
 * customer and product; only an order line of a DELIVERED order makes a customer eligible.
 * <p>
 * {@link Product#getRatingAverage()} / {@link Product#getRatingCount()} are derived from the
 * <em>visible</em> rows and are recomputed in the same transaction whenever one changes.
 * {@code updated_at} is the author's last edit: hiding / showing by an admin does not touch it.
 */
@Entity
@Table(
        name = "product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_reviews_user_product",
                columnNames = {"user_id", "product_id"}),
        indexes = {
                @Index(name = "idx_product_reviews_product", columnList = "product_id, visible"),
                @Index(name = "idx_product_reviews_user", columnList = "user_id"),
                @Index(name = "idx_product_reviews_created", columnList = "created_at")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ProductReview extends BaseEntity {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_TITLE = 100;
    public static final int MAX_COMMENT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_reviews_product")
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_reviews_user")
    )
    private User user;

    /** Whole stars, 1..5. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = MAX_TITLE)
    private String title;

    @Column(name = "comment", length = MAX_COMMENT)
    private String comment;

    /** Snapshot of the options on the delivered order line, e.g. "256GB · Purple"; null without variants. */
    @Column(name = "variant_label", length = 200)
    private String variantLabel;

    /** False once an admin hides it: then it is left out of the public list and the averages. */
    @Column(name = "visible", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean visible = true;
}
