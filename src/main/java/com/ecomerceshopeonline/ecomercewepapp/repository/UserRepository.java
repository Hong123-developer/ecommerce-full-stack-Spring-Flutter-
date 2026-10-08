package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** Admin customer list. Filtered to one role so administrators are not listed as shoppers. */
    @Query(value = """
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE r.name = :roleName
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR u.phone LIKE CONCAT('%', CAST(:search AS String), '%'))
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r
            WHERE r.name = :roleName
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR u.phone LIKE CONCAT('%', CAST(:search AS String), '%'))
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """)
    Page<User> adminSearch(@Param("roleName") RoleName roleName,
                           @Param("search") String search,
                           @Param("enabled") Boolean enabled,
                           Pageable pageable);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countByRole(@Param("roleName") RoleName roleName);

    /** New accounts of one role since a moment - administrators are not "new customers". */
    @Query("""
            SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r
            WHERE r.name = :roleName AND u.createdAt >= :since
            """)
    long countByRoleCreatedSince(@Param("roleName") RoleName roleName,
                                 @Param("since") LocalDateTime since);

    /** Rows of [day, signups] for one role - the registration history chart. */
    @Query("""
            SELECT CAST(u.createdAt AS date), COUNT(DISTINCT u)
            FROM User u JOIN u.roles r
            WHERE r.name = :roleName AND u.createdAt >= :since
            GROUP BY CAST(u.createdAt AS date)
            ORDER BY CAST(u.createdAt AS date)
            """)
    List<Object[]> registrationsByDay(@Param("roleName") RoleName roleName,
                                      @Param("since") LocalDateTime since);

    /** Account deletion - the user_roles join table has no entity of its own. */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId", nativeQuery = true)
    int deleteRoleLinks(@Param("userId") Long userId);

    /** Account deletion - the last step, once every referencing row is gone. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.id = :userId")
    int bulkDeleteById(@Param("userId") Long userId);
}
