package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddressRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AddressResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Address;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.exception.FieldValidationException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.AddressMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.AddressRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse get(Long userId, Long addressId) {
        return addressMapper.toResponse(require(userId, addressId));
    }

    @Override
    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        requireCoordinatePair(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        Address address = addressMapper.toEntity(request);
        address.setUser(user);

        // The very first address is always the default, whatever the request says.
        boolean isFirst = addressRepository.findByUserId(userId).isEmpty();
        boolean wantsDefault = isFirst || Boolean.TRUE.equals(request.isDefault());
        if (wantsDefault) {
            addressRepository.clearDefaults(userId);
        }
        address.setDefault(wantsDefault);

        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        requireCoordinatePair(request);
        Address address = require(userId, addressId);
        addressMapper.apply(request, address);

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            addressRepository.clearDefaults(userId);
            address.setDefault(true);
        }
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        Address address = require(userId, addressId);
        addressRepository.clearDefaults(userId);
        address.setDefault(true);
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = require(userId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            // Never leave the account without a default - promote whatever is left.
            addressRepository.findByUserId(userId).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                addressRepository.save(next);
            });
        }
    }

    /** Half a map pin is no pin: a latitude without its longitude cannot be shown or routed to. */
    private static void requireCoordinatePair(AddressRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new FieldValidationException(request.latitude() == null ? "latitude" : "longitude",
                    "Latitude and longitude must be given together");
        }
    }

    private Address require(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));
    }
}
