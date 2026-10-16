package com.ecomerceshopeonline.ecomercewepapp.dto.response;

/** How much there is in each deletable group - the numbers beside admin › Settings › Data. */
public record DataSummaryResponse(
        long orders,
        long payments,
        long notifications,
        long customers,
        /** Customers that still have orders (they can only go together with ORDERS). */
        long customersWithOrders,
        long products,
        long categories,
        long heroSlides,
        long deliveryOptions,
        boolean storeSettingsSaved,
        long admins
) {
}
