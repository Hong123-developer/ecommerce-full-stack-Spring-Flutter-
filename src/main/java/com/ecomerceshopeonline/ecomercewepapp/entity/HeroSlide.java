package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "hero_slides")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class HeroSlide extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Small kicker line above the title, e.g. "New season". */
    @Column(name = "eyebrow", length = 60)
    private String eyebrow;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "subtitle", length = 300)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "image_alt", length = 150)
    private String imageAlt;

    @Column(name = "primary_label", length = 40)
    private String primaryLabel;

    @Column(name = "primary_link", length = 300)
    private String primaryLink;

    @Column(name = "secondary_label", length = 40)
    private String secondaryLabel;

    @Column(name = "secondary_link", length = 300)
    private String secondaryLink;

    // Two optional "floating cards" beside the photo. A card shows only when it has an
    // image; otherwise the storefront fills that spot with a catalogue product.
    @Column(name = "card1_image_url", length = 500)
    private String card1ImageUrl;

    @Column(name = "card1_title", length = 40)
    private String card1Title;

    @Column(name = "card1_caption", length = 40)
    private String card1Caption;

    @Column(name = "card1_link", length = 300)
    private String card1Link;

    @Column(name = "card2_image_url", length = 500)
    private String card2ImageUrl;

    @Column(name = "card2_title", length = 40)
    private String card2Title;

    @Column(name = "card2_caption", length = 40)
    private String card2Caption;

    @Column(name = "card2_link", length = 300)
    private String card2Link;

    /**
     * Optional per-slide accent as upper-case {@code #RRGGBB}, e.g. {@code #D97706}. Null
     * means "no accent of its own" - the storefront falls back to its default palette.
     * Nullable on purpose: {@code ddl-auto=update} adds the column to existing rows as NULL.
     */
    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean enabled = true;
}
