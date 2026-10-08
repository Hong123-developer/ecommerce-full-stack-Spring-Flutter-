package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.StoreSettingsRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.StoreSettingsResponse;

public interface StoreSettingsService {

    /** The saved branding, or {@link StoreSettingsResponse#unsaved()} - never a 404. */
    StoreSettingsResponse get();

    /** Creates the single settings row on the first save and replaces its fields after. */
    StoreSettingsResponse save(StoreSettingsRequest request);
}
