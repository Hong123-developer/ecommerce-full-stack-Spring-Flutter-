package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_categories_name", columnNames = "name")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Display position set from the admin Categories page (move up / move down): lower comes
     * first. Equal values fall back to the name, which is how every category created before
     * ordering existed starts out (all 0, so alphabetical). New categories go to the end.
     */
    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int sortOrder = 0;

    @OneToMany(mappedBy = "category")
    @Builder.Default
    private List<Product> products = new ArrayList<>();
}
