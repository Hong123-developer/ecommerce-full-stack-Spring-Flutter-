package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.time.LocalDateTime;

/**
 * Store branding. A null field means "not set": the storefront uses its built-in value for
 * that field. {@code updatedAt} is null only while nothing has ever been saved.
 */
public record StoreSettingsResponse(
        String storeName,
        String logoUrl,
        boolean showNameWithLogo,
        String tagline,
        String supportEmail,
        String supportPhone,
        String address,
        LocalDateTime updatedAt
) {

    /** What GET returns before the first save - every field unset, the name shown. */
    public static StoreSettingsResponse unsaved() {
        return new StoreSettingsResponse(null, null, true, null, null, null, null, null);
    }
}
