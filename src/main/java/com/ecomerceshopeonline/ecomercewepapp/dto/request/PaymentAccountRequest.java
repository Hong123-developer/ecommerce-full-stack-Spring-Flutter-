package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the payment account shown to customers. Every field is optional; a
 * blank one is cleared. Strings are trimmed (blank becomes null) before validation runs.
 */
public record PaymentAccountRequest(
        @Size(max = 60, message = "Bank name must be at most 60 characters")
        String bankName,

        @Size(max = 100, message = "Account name must be at most 100 characters")
        String accountName,

        @Pattern(regexp = ACCOUNT_NUMBER, message = "Use 6-40 digits, spaces or dashes")
        String usdAccountNumber,

        @Pattern(regexp = ACCOUNT_NUMBER, message = "Use 6-40 digits, spaces or dashes")
        String khrAccountNumber
) {
    static final String ACCOUNT_NUMBER = "^[0-9][0-9 \\-]{4,38}[0-9]$";

    public PaymentAccountRequest {
        bankName = clean(bankName);
        accountName = clean(accountName);
        usdAccountNumber = clean(usdAccountNumber);
        khrAccountNumber = clean(khrAccountNumber);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
