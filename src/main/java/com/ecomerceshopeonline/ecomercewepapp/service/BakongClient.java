package com.ecomerceshopeonline.ecomercewepapp.service;

import java.util.Optional;

/** Thin wrapper over the Bakong Open API (https://api-bakong.nbc.gov.kh). */
public interface BakongClient {

    /**
     * Asks Bakong whether the QR identified by this MD5 has been paid.
     * Bakong has no webhook, so the client polls this.
     */
    TransactionCheck checkTransaction(String md5);

    /** Resolves the display name behind a Bakong account id, for showing on a receipt. */
    Optional<String> resolveAccountName(String accountId);

    record TransactionCheck(
            boolean paid,
            String transactionHash,
            String fromAccountId,
            String message
    ) {
        public static TransactionCheck pending(String message) {
            return new TransactionCheck(false, null, null, message);
        }
    }
}
