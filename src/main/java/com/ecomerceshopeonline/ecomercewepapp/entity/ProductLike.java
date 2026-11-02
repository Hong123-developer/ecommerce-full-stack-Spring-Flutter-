package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A "like" (heart / wishlist) that a shopper places on a product.
 * One row per (user, product) pair — the unique constraint prevents double-likes.
 */
@Entity
@Table(
        name = "product_likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_likes_user_product",
                columnNames = {"user_id", "product_id"}),
        indexes = {
                @Index(name = "idx_product_likes_user", columnList = "user_id"),
                @Index(name = "idx_product_likes_product", columnList = "product_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProductLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_likes_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_likes_product"))
    private Product product;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
