package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    Page<Category> findByEnabledTrue(Pageable pageable);

    boolean existsByName(String name);

    // ---------- display order (admin move up / move down) ----------

    /** Rows of [id, sortOrder] for every category, in display order - first category first. */
    @Query("SELECT c.id, c.sortOrder FROM Category c ORDER BY c.sortOrder ASC, c.name ASC, c.id ASC")
    List<Object[]> findDisplayOrder();

    /** The last slot's value, so a new category can go below it; -1 for no categories. */
    @Query("SELECT COALESCE(MAX(c.sortOrder), -1) FROM Category c")
    int findMaxSortOrder();

    /** Not clearAutomatically - callers load the moved category only after their updates. */
    @Modifying
    @Query("UPDATE Category c SET c.sortOrder = :sortOrder WHERE c.id = :id")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);
}
