package com.ecomerceshopeonline.ecomercewepapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything under the {@code app.*} prefix in application.properties, bound once at startup.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Otp otp,
        Oauth2 oauth2,
        Mail mail,
        Cors cors,
        Admin admin,
        Order order,
        Bakong bakong
) {

    public record Jwt(
            String secret,
            long accessTokenExpiration,
            long refreshTokenExpiration,
            String issuer
    ) {
    }

    public record Otp(
            int length,
            int expirationMinutes,
            int maxAttempts,
            int resendCooldownSeconds
    ) {
    }

    public record Oauth2(String redirectUri) {
    }

    public record Mail(String from, String appName) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Admin(String email, String password, String fullName) {
    }

    public record Order(BigDecimal deliveryFee, String numberPrefix) {
    }

    /**
     * KHQR generation and Bakong Open API access.
     *
     * @param accountId     Bakong account, e.g. {@code prol_menghong@bkrt}
     * @param token         Bearer token from https://api-bakong.nbc.gov.kh/register
     * @param expiryMinutes how long a dynamic QR stays scannable
     */
    public record Bakong(
            String apiUrl,
            String token,
            String accountId,
            String merchantName,
            String merchantCity,
            String currency,
            int expiryMinutes
    ) {
        public boolean isConfigured() {
            return accountId != null && !accountId.isBlank();
        }

        public boolean canQueryApi() {
            return token != null && !token.isBlank();
        }
    }
}
