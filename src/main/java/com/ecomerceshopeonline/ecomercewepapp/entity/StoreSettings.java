package com.ecomerceshopeonline.ecomercewepapp.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

/**
 * Store branding the admin can edit: name, logo, tagline and contact details shown in the
 * storefront header and footer.
 * <p>
 * A singleton: the id is assigned (always {@link #SINGLETON_ID}), never generated, and a
 * check constraint pins it, so the table can never hold more than one row. Until the
 * admin saves for the first time there is no row at all and the storefront falls back to
 * its built-in branding.
 */
@Entity
@Table(
        name = "store_settings",
        check = @CheckConstraint(name = "ck_store_settings_singleton", constraint = "id = 1")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class StoreSettings extends BaseEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "store_name", nullable = false, length = 60)
    private String storeName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** False hides the store name next to the logo - for logos that already contain it. */
    @Column(name = "show_name_with_logo", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean showNameWithLogo = true;

    @Column(name = "tagline", length = 200)
    private String tagline;

    @Column(name = "support_email", length = 120)
    private String supportEmail;

    @Column(name = "support_phone", length = 30)
    private String supportPhone;

    @Column(name = "address", length = 200)
    private String address;
}
