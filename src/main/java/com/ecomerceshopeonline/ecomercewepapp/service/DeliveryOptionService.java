package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DeliveryOptionRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DeliveryOptionResponse;

import java.util.List;

public interface DeliveryOptionService {

    List<DeliveryOptionResponse> browse();

    // --- admin ---

    List<DeliveryOptionResponse> listAll();

    DeliveryOptionResponse getById(Long id);

    DeliveryOptionResponse create(DeliveryOptionRequest request);

    /** Refuses to disable the last enabled option. */
    DeliveryOptionResponse update(Long id, DeliveryOptionRequest request);

    /** Refuses to disable the last enabled option. */
    DeliveryOptionResponse setEnabled(Long id, boolean enabled);

    /** Sets each option's sort order to its index in {@code ids}, which must list every option once. */
    List<DeliveryOptionResponse> reorder(List<Long> ids);

    /** Refuses the last enabled option, and any option an order refers to (disable it instead). */
    void delete(Long id);
}
