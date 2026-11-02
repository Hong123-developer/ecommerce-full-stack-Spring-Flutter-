package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddressRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {

    List<AddressResponse> list(Long userId);

    AddressResponse get(Long userId, Long addressId);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    AddressResponse setDefault(Long userId, Long addressId);

    void delete(Long userId, Long addressId);
}
