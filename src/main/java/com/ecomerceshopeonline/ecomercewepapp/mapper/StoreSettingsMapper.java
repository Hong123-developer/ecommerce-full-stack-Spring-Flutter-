package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.StoreSettingsRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.StoreSettingsResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import org.springframework.stereotype.Component;

@Component
public class StoreSettingsMapper {

    public StoreSettingsResponse toResponse(StoreSettings settings) {
        return new StoreSettingsResponse(
                settings.getStoreName(),
                settings.getLogoUrl(),
                settings.isShowNameWithLogo(),
                settings.getTagline(),
                settings.getSupportEmail(),
                settings.getSupportPhone(),
                settings.getAddress(),
                settings.getUpdatedAt());
    }

    /**
     * Overwrites every editable field - a save is a full replacement, so an omitted
     * optional field is cleared. The request has already trimmed blanks to null.
     * Id and audit columns are left alone.
     */
    public void apply(StoreSettingsRequest request, StoreSettings target) {
        target.setStoreName(request.storeName());
        target.setLogoUrl(request.logoUrl());
        target.setShowNameWithLogo(
                request.showNameWithLogo() == null || request.showNameWithLogo());
        target.setTagline(request.tagline());
        target.setSupportEmail(request.supportEmail());
        target.setSupportPhone(request.supportPhone());
        target.setAddress(request.address());
    }
}
