package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.DeliveryOption;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOptionRepository extends JpaRepository<DeliveryOption, Long> {

    /** Checkout choices - enabled options only. The id tie-break keeps equal sort orders stable. */
    List<DeliveryOption> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** Admin listing - includes disabled options, same ordering as the public one. */
    List<DeliveryOption> findAllByOrderBySortOrderAscIdAsc();

    /** What a checkout that names no option gets: the first enabled option of that type. */
    Optional<DeliveryOption> findFirstByTypeAndEnabledTrueOrderBySortOrderAscIdAsc(DeliveryType type);

    boolean existsByEnabledTrue();

    /** Highest sort order in use, or -1 when there are no options, so "max + 1" starts at 0. */
    @Query("SELECT COALESCE(MAX(d.sortOrder), -1) FROM DeliveryOption d")
    int findMaxSortOrder();

    /**
     * Every enabled option, row-locked in id order. Taken before disabling or deleting one,
     * so two admins switching off the last two options at the same moment cannot both win
     * and leave the checkout with nothing to offer.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DeliveryOption d WHERE d.enabled = true ORDER BY d.id")
    List<DeliveryOption> lockAllEnabled();

    /** Orders that refer to the option - those keep it from being deleted. */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.deliveryOption.id = :optionId")
    long countOrdersUsing(@Param("optionId") Long optionId);
}
