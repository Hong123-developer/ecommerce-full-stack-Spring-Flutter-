    package com.ecomerceshopeonline.ecomercewepapp.entity;

    import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
    import jakarta.persistence.*;
    import lombok.Builder;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;
    import lombok.experimental.SuperBuilder;

    import java.util.ArrayList;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;

    @Entity
    @Table(
            name = "users",
            uniqueConstraints = {
                    @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                    @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
            },
            indexes = @Index(name = "idx_users_phone", columnList = "phone")
    )
    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    public class User extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "full_name", nullable = false, length = 150)
        private String fullName;

        @Column(name = "email", nullable = false, length = 180)
        private String email;

        @Column(name = "phone", length = 30)
        private String phone;

        /** BCrypt hash. Null for accounts that only ever sign in through an OAuth provider. */
        @Column(name = "password", length = 255)
        private String password;

        @Column(name = "profile_image", length = 500)
        private String profileImage;

        @Enumerated(EnumType.STRING)
        @Column(name = "provider", nullable = false, length = 20)
        @Builder.Default
        private AuthProvider provider = AuthProvider.LOCAL;

        /** The provider's own user id (e.g. the Google "sub" claim). Null for local accounts. */
        @Column(name = "provider_id", length = 120)
        private String providerId;

        @Column(name = "email_verified", nullable = false)
        @Builder.Default
        private boolean emailVerified = false;

        @Column(name = "enabled", nullable = false)
        @Builder.Default
        private boolean enabled = true;

        @ManyToMany(fetch = FetchType.EAGER)
        @JoinTable(
                name = "user_roles",
                joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_roles_user")),
                inverseJoinColumns = @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_roles_role"))
        )
        @Builder.Default
        private Set<Role> roles = new HashSet<>();

        @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
        @Builder.Default
        private List<Address> addresses = new ArrayList<>();

        @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
        private Cart cart;

        @OneToMany(mappedBy = "user")
        @Builder.Default
        private List<Order> orders = new ArrayList<>();

        public void addRole(Role role) {
            this.roles.add(role);
        }

        public void addAddress(Address address) {
            this.addresses.add(address);
            address.setUser(this);
        }

    }
