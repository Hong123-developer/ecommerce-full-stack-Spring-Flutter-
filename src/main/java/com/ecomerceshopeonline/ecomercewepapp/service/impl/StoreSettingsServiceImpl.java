package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.StoreSettingsRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.StoreSettingsResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import com.ecomerceshopeonline.ecomercewepapp.mapper.StoreSettingsMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.StoreSettingsRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSettingsServiceImpl implements StoreSettingsService {

    private final StoreSettingsRepository storeSettingsRepository;
    private final StoreSettingsMapper storeSettingsMapper;

    @Override
    @Transactional(readOnly = true)
    public StoreSettingsResponse get() {
        return storeSettingsRepository.findSingleton()
                .map(storeSettingsMapper::toResponse)
                .orElseGet(StoreSettingsResponse::unsaved);
    }

    @Override
    @Transactional
    public StoreSettingsResponse save(StoreSettingsRequest request) {
        StoreSettings settings = storeSettingsRepository.findSingleton()
                .orElseGet(() -> StoreSettings.builder().id(StoreSettings.SINGLETON_ID).build());
        storeSettingsMapper.apply(request, settings);
        // Flushed now rather than at commit so the auditing listener has already stamped
        // updatedAt when the response is built - the storefront can use it to cache-bust.
        return storeSettingsMapper.toResponse(storeSettingsRepository.saveAndFlush(settings));
    }
}
