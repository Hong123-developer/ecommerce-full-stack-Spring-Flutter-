package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddressRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AddressResponse;

import java.util.List;

/** Every method is scoped by userId - one shopper can never read or edit another's address. */
public interface AddressService {

    List<AddressResponse> list(Long userId);

    AddressResponse get(Long userId, Long addressId);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    AddressResponse setDefault(Long userId, Long addressId);

    void delete(Long userId, Long addressId);
}
