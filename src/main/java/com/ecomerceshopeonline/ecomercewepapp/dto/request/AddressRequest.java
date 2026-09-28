package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "Recipient name is required")
        @Size(max = 150, message = "Recipient name must be at most 150 characters")
        String recipientName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{8,30}$", message = "Phone must be 8-30 digits")
        String phone,

        @NotBlank(message = "Province is required")
        @Size(max = 100, message = "Province must be at most 100 characters")
        String province,

        @Size(max = 100) String district,
        @Size(max = 100) String commune,
        @Size(max = 100) String village,

        @Size(max = 500, message = "Address detail must be at most 500 characters")
        String addressDetail,

        /** When true, every other address for this user is demoted. */
        Boolean isDefault
) {
}
