package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Size;

/** Admin confirmation that money actually arrived. */
public record VerifyPaymentRequest(

        /** Bank/Bakong reference, if the admin has one to record. */
        @Size(max = 120, message = "Transaction id must be at most 120 characters")
        String transactionId
) {
}
