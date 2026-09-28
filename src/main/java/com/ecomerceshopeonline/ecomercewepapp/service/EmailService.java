package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;

public interface EmailService {

    void sendVerificationCode(String to, String fullName, String code,
                              VerificationPurpose purpose, int validMinutes);

    void sendWelcome(String to, String fullName);
}
