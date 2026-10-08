package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        String recipientName,
        String phone,
        String province,
        String district,
        String commune,
        String village,
        String addressDetail,
        /** Map pin; both null when the address has none. */
        Double latitude,
        Double longitude,
        boolean isDefault,
        /** Flattened one-line form, the same text snapshotted onto an order. */
        String fullAddress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
