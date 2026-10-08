package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "addresses",
        indexes = @Index(name = "idx_addresses_user", columnList = "user_id")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_addresses_user")
    )
    private User user;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "commune", length = 100)
    private String commune;

    @Column(name = "village", length = 100)
    private String village;

    @Column(name = "address_detail", length = 500)
    private String addressDetail;

    /**
     * Map pin set with "Auto location" (browser GPS) or "Pick on map". Both are set or both
     * are null; addresses saved before the map existed have none.
     */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    /** Flattened one-line form, snapshotted onto an order at checkout. */
    public String toSingleLine() {
        return String.join(", ",
                java.util.stream.Stream.of(addressDetail, village, commune, district, province)
                        .filter(part -> part != null && !part.isBlank())
                        .toList());
    }
}
