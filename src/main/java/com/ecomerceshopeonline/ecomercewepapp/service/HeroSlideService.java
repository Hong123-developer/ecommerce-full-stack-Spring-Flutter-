package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.HeroSlideRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.HeroSlideResponse;

import java.util.List;

public interface HeroSlideService {

    /** Public carousel - enabled slides only, in display order. Small, so not paged. */
    List<HeroSlideResponse> browse();

    // --- admin ---

    List<HeroSlideResponse> listAll();

    HeroSlideResponse getById(Long id);

    HeroSlideResponse create(HeroSlideRequest request);

    HeroSlideResponse update(Long id, HeroSlideRequest request);

    HeroSlideResponse setEnabled(Long id, boolean enabled);

    /** Sets each slide's sort order to its index in {@code ids}, which must list every slide once. */
    List<HeroSlideResponse> reorder(List<Long> ids);

    void delete(Long id);
}
