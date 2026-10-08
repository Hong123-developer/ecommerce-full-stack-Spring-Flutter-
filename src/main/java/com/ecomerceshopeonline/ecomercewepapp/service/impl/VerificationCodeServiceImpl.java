package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.VerificationCode;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.repository.VerificationCodeRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import com.ecomerceshopeonline.ecomercewepapp.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void issueAndSend(User user, VerificationPurpose purpose) {
        AppProperties.Otp otp = properties.otp();
        LocalDateTime now = LocalDateTime.now();

        verificationCodeRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .ifPresent(previous -> {
                    LocalDateTime nextAllowed = previous.getCreatedAt()
                            .plusSeconds(otp.resendCooldownSeconds());
                    if (now.isBefore(nextAllowed)) {
                        throw new BadRequestException("Please wait "
                                + otp.resendCooldownSeconds()
                                + " seconds before requesting another code");
                    }
                    // Only one code per purpose may be live at a time.
                    previous.setUsed(true);
                    verificationCodeRepository.save(previous);
                });

        String rawCode = generateCode(otp.length());
        VerificationCode code = VerificationCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(rawCode))
                .purpose(purpose)
                .expiresAt(now.plusMinutes(otp.expirationMinutes()))
                .attempts(0)
                .used(false)
                .build();
        verificationCodeRepository.save(code);

        emailService.sendVerificationCode(user.getEmail(), user.getFullName(), rawCode,
                purpose, otp.expirationMinutes());
    }

    @Override
    @Transactional
    public boolean issueAndSendUnlessRecent(User user, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        int cooldown = properties.otp().resendCooldownSeconds();
        boolean sentRecently = verificationCodeRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(previous -> !previous.isExpired(now))
                .map(previous -> now.isBefore(previous.getCreatedAt().plusSeconds(cooldown)))
                .orElse(false);
        if (sentRecently) {
            return false;
        }
        issueAndSend(user, purpose);
        return true;
    }

    @Override
    @Transactional
    public void consume(User user, VerificationPurpose purpose, String rawCode) {
        LocalDateTime now = LocalDateTime.now();

        VerificationCode code = verificationCodeRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new BadRequestException(
                        "No active verification code. Please request a new one."));

        if (code.isExpired(now)) {
            throw new BadRequestException("This code has expired. Please request a new one.");
        }

        if (code.getAttempts() >= properties.otp().maxAttempts()) {
            // Burn the code so an attacker cannot keep guessing against the same row.
            code.setUsed(true);
            verificationCodeRepository.save(code);
            throw new BadRequestException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(rawCode, code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            verificationCodeRepository.save(code);
            int remaining = properties.otp().maxAttempts() - code.getAttempts();
            throw new BadRequestException("Incorrect code. " + Math.max(remaining, 0)
                    + " attempt(s) remaining.");
        }

        code.setUsed(true);
        verificationCodeRepository.save(code);
    }

    private String generateCode(int length) {
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", secureRandom.nextInt(bound));
    }
}
