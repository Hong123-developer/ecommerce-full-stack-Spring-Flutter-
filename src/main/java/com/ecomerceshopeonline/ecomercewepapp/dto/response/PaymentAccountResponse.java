package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.PaymentAccount;

import java.time.LocalDateTime;

/** The account customers pay into; every field is null until the admin saves one. */
public record PaymentAccountResponse(
        String bankName,
        String accountName,
        String usdAccountNumber,
        String khrAccountNumber,
        LocalDateTime updatedAt
) {

    public static PaymentAccountResponse unsaved() {
        return new PaymentAccountResponse(null, null, null, null, null);
    }

    public static PaymentAccountResponse of(PaymentAccount account) {
        return new PaymentAccountResponse(account.getBankName(), account.getAccountName(),
                account.getUsdAccountNumber(), account.getKhrAccountNumber(), account.getUpdatedAt());
    }
}
