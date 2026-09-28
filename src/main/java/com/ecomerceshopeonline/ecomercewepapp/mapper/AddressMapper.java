package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.AddressRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AddressResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getProvince(),
                address.getDistrict(),
                address.getCommune(),
                address.getVillage(),
                address.getAddressDetail(),
                address.isDefault(),
                address.toSingleLine(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }

    public Address toEntity(AddressRequest request) {
        Address address = new Address();
        apply(request, address);
        return address;
    }

    public void apply(AddressRequest request, Address target) {
        target.setRecipientName(request.recipientName().trim());
        target.setPhone(request.phone().trim());
        target.setProvince(request.province().trim());
        target.setDistrict(request.district());
        target.setCommune(request.commune());
        target.setVillage(request.village());
        target.setAddressDetail(request.addressDetail());
    }
}
