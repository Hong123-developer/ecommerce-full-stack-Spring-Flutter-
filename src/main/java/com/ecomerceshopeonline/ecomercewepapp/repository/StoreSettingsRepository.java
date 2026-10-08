package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {

    /** The one settings row, or empty when the admin has never saved any. */
    default Optional<StoreSettings> findSingleton() {
        return findById(StoreSettings.SINGLETON_ID);
    }
}
