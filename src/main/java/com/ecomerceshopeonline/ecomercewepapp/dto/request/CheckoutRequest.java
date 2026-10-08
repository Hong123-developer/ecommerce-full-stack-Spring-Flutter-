package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Turns the cart into an order.
 * <p>
 * Which other fields are required depends on the delivery option, so the service checks
 * them: a {@code DELIVERY} option needs {@code addressId} (blank contact fields then default
 * to the address's recipient and phone), while {@code PICKUP} ignores {@code addressId} and
 * needs {@code contactName} and {@code contactPhone}.
 * <p>
 * The compact constructor trims the free-text fields and turns blank into null
 * <em>before</em> validation runs, so the limits apply to the trimmed text and a blank
 * phone is "not given" rather than "invalid".
 */
public record CheckoutRequest(

        /** Saved address to deliver to. Required for a DELIVERY option, ignored for PICKUP. */
        Long addressId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        /**
         * One of {@code GET /api/v1/delivery-options}. Null means the first enabled DELIVERY
         * option - what clients from before delivery options existed get.
         */
        Long deliveryOptionId,

        @Size(max = 150, message = "Customer name must be at most 150 characters")
        String contactName,

        @Pattern(regexp = "^[0-9+\\-\\s]{8,30}$", message = "Phone must be 8-30 digits")
        String contactPhone,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note
) {

    public CheckoutRequest {
        contactName = clean(contactName);
        contactPhone = clean(contactPhone);
        note = clean(note);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
