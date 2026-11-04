package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.ShipmentStage;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * The courier's side of a shipped order, as the admin's shipment form sends it.
 * <p>
 * Full replacement: every save sends the whole form, and an omitted or blank field clears
 * that value. The compact constructor trims every string and turns blank into null
 * <em>before</em> validation runs, so the length limits apply to the trimmed text and a
 * blank phone is "not given" rather than "invalid".
 * <p>
 * {@code stage} may only be set while the order is SHIPPED and is not a pickup order; the
 * service checks that, since it depends on the order.
 */
public record UpdateShipmentRequest(

        @Size(max = 60, message = "Tracking number must be at most 60 characters")
        String trackingNumber,

        @Size(max = 80, message = "Carrier name must be at most 80 characters")
        String carrierName,

        @Size(max = 120, message = "Courier name must be at most 120 characters")
        String courierName,

        @Pattern(regexp = "^[+0-9 ()-]{6,30}$",
                message = "Courier phone must be 6-30 digits, spaces or + ( ) -")
        String courierPhone,

        /** When the store expects it to arrive, e.g. {@code 2026-11-03T16:30:00}. */
        LocalDateTime estimatedDeliveryAt,

        /** The courier's step; null leaves the order without one. */
        ShipmentStage stage
) {

    public UpdateShipmentRequest {
        trackingNumber = clean(trackingNumber);
        carrierName = clean(carrierName);
        courierName = clean(courierName);
        courierPhone = clean(courierPhone);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
