package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One entry of {@code GET /api/v1/payments/methods}: whether checkout may offer it right now.
 *
 * @param code     the {@code paymentMethod} value checkout accepts, e.g. "CARD"
 * @param provider who processes it, e.g. "ABA PayWay" (card only, and only while enabled)
 * @param brands   accepted card brands (card only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethodResponse(
        String code,
        boolean enabled,
        String provider,
        List<String> brands
) {

    public static PaymentMethodResponse of(String code, boolean enabled) {
        return new PaymentMethodResponse(code, enabled, null, null);
    }
}
