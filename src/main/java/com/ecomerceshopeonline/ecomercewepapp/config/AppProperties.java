package com.ecomerceshopeonline.ecomercewepapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.util.unit.DataSize;

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
        Bakong bakong,
        Upload upload,
        Payway payway
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

    /**
     * Where uploaded images are written, and the largest single image accepted.
     * Files are served back under {@code /uploads/**}.
     */
    public record Upload(String dir, DataSize maxImageSize) {
    }

    /**
     * Card payments through ABA PayWay's hosted checkout (https://developer.payway.com.kh).
     * Card stays switched off - hidden at checkout and refused by the API - while
     * {@code merchant-id} or {@code api-key} is blank.
     *
     * @param baseUrl         {@code https://checkout-sandbox.payway.com.kh} or, live,
     *                        {@code https://checkout.payway.com.kh}
     * @param merchantId      merchant id from the PayWay welcome e-mail
     * @param apiKey          API key (HMAC secret) - server side only, never sent to a browser
     * @param callbackUrl     public https URL of {@code POST /api/v1/payments/payway/callback};
     *                        blank = no pushback, the payment page's polling settles instead
     * @param frontendUrl     where the shopper's browser returns to after paying
     * @param lifetimeMinutes how long one card attempt stays payable (PayWay minimum: 3)
     * @param viewType        PayWay's {@code view_type}: {@code popup} (default when blank) shows
     *                        the card page in a popup over the shop; {@code hosted_view} keeps
     *                        the same-tab redirect to PayWay's page
     */
    public record Payway(
            String baseUrl,
            String merchantId,
            String apiKey,
            String callbackUrl,
            String frontendUrl,
            int lifetimeMinutes,
            String viewType
    ) {
        public boolean isConfigured() {
            return merchantId != null && !merchantId.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }

        /** The generated toString would print the API key into any log that dumps the config. */
        @Override
        public String toString() {
            return "Payway[baseUrl=" + baseUrl + ", merchantId=" + merchantId
                    + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<blank>" : "<set>")
                    + ", callbackUrl=" + callbackUrl + ", frontendUrl=" + frontendUrl
                    + ", lifetimeMinutes=" + lifetimeMinutes + ", viewType=" + viewType + "]";
        }
    }
}
