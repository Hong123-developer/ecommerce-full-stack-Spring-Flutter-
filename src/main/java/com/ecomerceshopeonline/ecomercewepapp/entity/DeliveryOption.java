package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;


@Entity
@Table(name = "delivery_options")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DeliveryOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shown to the shopper, e.g. "J&T Express". */
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    private DeliveryType type;

    @Column(name = "fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    /** Free text such as "1-2 days" or "Same day". */
    @Column(name = "estimated_time", length = 60)
    private String estimatedTime;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int sortOrder = 0;
}
