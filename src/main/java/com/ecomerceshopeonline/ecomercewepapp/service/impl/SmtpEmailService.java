package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Override
    public void sendVerificationCode(String to, String fullName, String code,
                                     VerificationPurpose purpose, int validMinutes) {
        log.info("========================================================================");
        log.info("[OTP CODE] Purpose: {}, To: {}, CODE = {}", purpose, to, code);
        log.info("========================================================================");
        String subject = switch (purpose) {
            case EMAIL_VERIFICATION -> "Verify your email address";
            case PASSWORD_RESET -> "Reset your password";
            case PHONE_VERIFICATION -> "Verify your phone number";
        };
        String intro = switch (purpose) {
            case EMAIL_VERIFICATION -> "Thanks for signing up. Use the code below to verify your email address.";
            case PASSWORD_RESET -> "We received a request to reset your password. Use the code below to continue.";
            case PHONE_VERIFICATION -> "Use the code below to verify your phone number.";
        };

        send(to, subject, """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#111">%s</h2>
                  <p>Hi %s,</p>
                  <p>%s</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;margin:24px 0;color:#111">%s</p>
                  <p style="color:#666">This code expires in %d minutes. If you did not request it, you can ignore this email.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                  <p style="color:#999;font-size:12px">%s</p>
                </div>
                """.formatted(subject, escape(fullName), intro, code, validMinutes,
                properties.mail().appName()));
    }

    @Override
    public void sendWelcome(String to, String fullName) {
        send(to, "Welcome to " + properties.mail().appName(), """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#111">Welcome, %s</h2>
                  <p>Your email is verified and your account is ready to use. Happy shopping!</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                  <p style="color:#999;font-size:12px">%s</p>
                </div>
                """.formatted(escape(fullName), properties.mail().appName()));
    }

    private void send(String to, String subject, String html) {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.warn(" [DEV MODE] spring.mail.username is not configured. Email '{}' was logged to console and not sent over network.", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent '{}' email to {}", subject, to);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.error("Failed to send '{}' email to {}", subject, to, ex);
            throw new ApiException(
                    "Could not send the email. Please check the mail configuration and try again.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** Names end up inside an HTML document, so close off the obvious injection route. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
