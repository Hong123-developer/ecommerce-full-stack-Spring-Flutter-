package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.HeroSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HeroSlideRepository extends JpaRepository<HeroSlide, Long> {

    /** Public carousel - enabled slides only. The id tie-break keeps equal sort orders stable. */
    List<HeroSlide> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** Admin listing - includes disabled slides, same ordering as the public one. */
    List<HeroSlide> findAllByOrderBySortOrderAscIdAsc();

    /** Highest sort order in use, or -1 when there are no slides, so "max + 1" starts at 0. */
    @Query("SELECT COALESCE(MAX(h.sortOrder), -1) FROM HeroSlide h")
    int findMaxSortOrder();
}
