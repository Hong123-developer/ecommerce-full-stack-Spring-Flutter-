package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or replace a saved address. Update is full replacement, like every field here:
 * a PUT without {@code latitude}/{@code longitude} removes the map pin.
 */
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
        Boolean isDefault,

        /**
         * Map pin from "Auto location" or "Pick on map". Optional, but latitude and longitude
         * go together. The range checks also reject NaN and infinity.
         */
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude
) {
}
