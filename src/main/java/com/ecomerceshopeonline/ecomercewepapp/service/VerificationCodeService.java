package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;

public interface VerificationCodeService {

    /** Generates a fresh 6-digit code, stores its hash, and emails the plain code to the user. */
    void issueAndSend(User user, VerificationPurpose purpose);

    /**
     * Like {@link #issueAndSend} but never trips the resend cooldown: if a still-valid code
     * was sent within the cooldown window, nothing is sent and {@code false} is returned.
     * Returns {@code true} when a fresh code was generated and emailed.
     */
    boolean issueAndSendUnlessRecent(User user, VerificationPurpose purpose);

    /**
     * Checks a submitted code and marks it used on success.
     * Throws if the code is wrong, expired, already used, or out of attempts.
     */
    void consume(User user, VerificationPurpose purpose, String rawCode);
}
