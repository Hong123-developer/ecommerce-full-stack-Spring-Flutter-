package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        int itemCount,
        int totalQuantity,
        BigDecimal subtotal,
        /** True when every line is still purchasable - the frontend can gate the checkout button. */
        boolean checkoutReady
) {
}
