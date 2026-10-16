package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Rating overview of one product, over its visible reviews.
 *
 * @param average      one decimal, 0 without reviews
 * @param count        number of visible reviews
 * @param distribution reviews per star value, keys "5".."1" (in that order), every key present
 */
public record ReviewSummaryResponse(
        BigDecimal average,
        long count,
        Map<String, Long> distribution
) {
}
