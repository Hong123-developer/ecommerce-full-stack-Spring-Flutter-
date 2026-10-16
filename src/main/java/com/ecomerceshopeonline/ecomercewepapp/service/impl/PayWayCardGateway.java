package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway.CardTransactionResult.Status;
import com.ecomerceshopeonline.ecomercewepapp.util.PayWayHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ABA PayWay hosted checkout (https://developer.payway.com.kh, "Ecommerce Checkout").
 * <p>
 * The browser posts the signed form from {@link #createCheckout} to PayWay, which shows its
 * own card page (Visa, Mastercard, UnionPay, JCB). The outcome is read back with Check
 * Transaction ({@link #check}) - never taken from a callback body.
 * <p>
 * Things to confirm with the first sandbox credentials (the docs leave them open):
 * <ul>
 *   <li>Whitelisting: PayWay only answers whitelisted domains / server IPs (error 6 on
 *       purchase, 81 for a non-whitelisted {@code return_url}). Ask PayWay to add the shop's
 *       domain and this server's IP. Some SDKs also send a {@code Referer} header on the
 *       server-to-server calls; add one here if Check Transaction refuses the server.</li>
 *   <li>{@code amount} carries the whole order total (delivery included) and no separate
 *       {@code shipping} is sent, so the shopper is charged exactly the order total whether or
 *       not PayWay would add shipping on top.</li>
 *   <li>Which Check Transaction amount to compare: {@code original_amount} (what was asked
 *       for) is used, falling back to {@code total_amount} / {@code payment_amount}.</li>
 *   <li>{@code tran_id} is digits only (the docs' examples are; letters are not documented).</li>
 *   <li>{@code view_type} ({@code app.payway.view-type}): {@code popup} by default - the shop
 *       loads PayWay's checkout2-0.js and PayWay shows its card form in a popup over the
 *       checkout; it then sends the popup's iframe to the {@code frame=1} return URLs below.
 *       {@code hosted_view} keeps the old plain same-tab POST, and {@code view_type} is then
 *       not sent at all. {@code view_type} is not part of the purchase hash.</li>
 * </ul>
 */
@Slf4j
@Component
public class PayWayCardGateway implements CardPaymentGateway {

    public static final String PROVIDER = "ABA PayWay";

    /** PayWay's {@code view_type} values (Purchase API). */
    public static final String VIEW_POPUP = "popup";
    public static final String VIEW_HOSTED = "hosted_view";

    private static final String DEFAULT_BASE_URL = "https://checkout-sandbox.payway.com.kh";
    /** PayWay refuses a lifetime under 3 minutes (error 69). */
    private static final int MIN_LIFETIME_MINUTES = 3;
    private static final int DEFAULT_LIFETIME_MINUTES = 30;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_ITEM_NAME = 100;
    private static final int MAX_EMAIL = 50;

    private final AppProperties.Payway config;
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Autowired
    public PayWayCardGateway(AppProperties properties) {
        this(properties, RestClient.builder().requestFactory(timeouts()));
    }

    /** For tests: the builder may be bound to a mock server. */
    public PayWayCardGateway(AppProperties properties, RestClient.Builder builder) {
        this.config = properties.payway();
        // Absolute URLs per call rather than a baseUrl: the base may legitimately be blank.
        this.restClient = builder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    /** {@code app.payway.lifetime-minutes}, never below PayWay's minimum of 3. */
    @Override
    public int attemptLifetimeMinutes() {
        int configured = config == null || config.lifetimeMinutes() <= 0
                ? DEFAULT_LIFETIME_MINUTES
                : config.lifetimeMinutes();
        return Math.max(MIN_LIFETIME_MINUTES, configured);
    }

    // ------------------------------------------------------------------ purchase

    @Override
    public CardCheckout createCheckout(Order order, Payment payment, String tranId) {
        requireConfigured();
        boolean popup = isPopup();
        String currency = payment.getCurrency();
        String returnTo = trimTrailingSlash(blankToNull(config.frontendUrl()) == null
                ? "http://localhost:5173" : config.frontendUrl().trim())
                + "/payment/" + order.getId() + "?from=card";
        // Popup: PayWay sends its iframe (inside our page) to these URLs; frame=1 makes the
        // payment page there tell the checkout underneath and close, instead of opening the
        // whole shop inside the popup.
        String successUrl = popup ? returnTo + "&frame=1" : returnTo;
        String cancelUrl = popup ? returnTo + "&frame=1&cancelled=1" : returnTo;
        String callback = blankToNull(config.callbackUrl());

        // Inserted in PayWayHash.PURCHASE_HASH_FIELDS order; blank values are left out.
        Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "req_time", PayWayHash.reqTime(Instant.now()));
        put(fields, "merchant_id", config.merchantId().trim());
        put(fields, "tran_id", tranId);
        put(fields, "amount", PayWayHash.formatAmount(order.getTotalAmount(), currency));
        put(fields, "items", itemsOf(order));
        put(fields, "email", emailOf(order));
        put(fields, "type", "purchase");
        put(fields, "payment_option", "cards");
        // Where PayWay POSTs its pushback; without it PayWay uses the profile's default, and
        // the payment page's polling settles the order anyway.
        put(fields, "return_url", callback == null ? null : PayWayHash.base64(callback.trim()));
        put(fields, "cancel_url", cancelUrl);
        put(fields, "continue_success_url", successUrl);
        put(fields, "currency", currency);
        put(fields, "return_params", order.getOrderNumber());
        put(fields, "lifetime", String.valueOf(attemptLifetimeMinutes()));
        // Signed before view_type is added: the docs leave view_type out of the hash.
        String hash = PayWayHash.purchaseHash(fields, config.apiKey());
        if (popup) {
            fields.put("view_type", VIEW_POPUP);
        }
        fields.put("hash", hash);

        return new CardCheckout(baseUrl() + PayWayHash.PURCHASE_PATH, "POST",
                popup ? CardCheckout.DISPLAY_POPUP : CardCheckout.DISPLAY_REDIRECT,
                Collections.unmodifiableMap(fields));
    }

    /**
     * {@code app.payway.view-type}: only {@code hosted_view} gives the old same-tab page;
     * blank or anything else is the popup.
     */
    private boolean isPopup() {
        String viewType = config == null ? null : blankToNull(config.viewType());
        return viewType == null || !VIEW_HOSTED.equalsIgnoreCase(viewType.trim());
    }

    /** Base64 JSON of the order lines - shown on PayWay's page only, not validated against the amount. */
    private String itemsOf(Order order) {
        List<Map<String, Object>> items = order.getItems().stream()
                .limit(MAX_ITEMS)
                .map(this::itemOf)
                .toList();
        if (items.isEmpty()) {
            return null;
        }
        return PayWayHash.base64(json.writeValueAsString(items));
    }

    private Map<String, Object> itemOf(OrderItem item) {
        String name = item.getVariantLabel() == null
                ? item.getProductName()
                : item.getProductName() + " (" + item.getVariantLabel() + ")";
        if (name.length() > MAX_ITEM_NAME) {
            name = name.substring(0, MAX_ITEM_NAME);
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("name", name);
        line.put("quantity", item.getQuantity());
        line.put("price", item.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
        return line;
    }

    /** Pre-fills PayWay's receipt e-mail; left out when PayWay would reject it (max 50). */
    private String emailOf(Order order) {
        String email = order.getUser() == null ? null : blankToNull(order.getUser().getEmail());
        if (email == null || email.length() > MAX_EMAIL || !email.contains("@")) {
            return null;
        }
        return email.trim();
    }

    // ------------------------------------------------------------------ check

    @Override
    public CardTransactionResult check(String tranId) {
        requireConfigured();
        JsonNode root = post(PayWayHash.CHECK_TRANSACTION_PATH, tranId);
        if (root == null) {
            return CardTransactionResult.of(Status.PENDING, "No answer from PayWay");
        }

        // The schema nests "status" inside "data", the examples put it at the top level.
        JsonNode status = root.path("status");
        if (!status.isObject()) {
            status = root.path("data").path("status");
        }
        // The examples mix "00" and 6 - compare as text.
        String code = text(status.path("code"));
        String message = text(status.path("message"));
        if (code == null) {
            return CardTransactionResult.of(Status.PENDING, "No status in PayWay's answer");
        }
        if ("6".equals(code)) {
            return CardTransactionResult.of(Status.NOT_FOUND, message);
        }
        if (!"00".equals(code) && !"0".equals(code)) {
            // 5 wrong hash, 8 merchant profile, 11 internal, 429 rate limit - nothing to settle on.
            log.warn("PayWay check-transaction for tran {} answered code {} ({})", tranId, code, message);
            return CardTransactionResult.of(Status.PENDING, code + " " + message);
        }

        JsonNode data = root.path("data");
        Integer statusCode = integer(data.path("payment_status_code"));
        String paymentStatus = text(data.path("payment_status"));
        String upper = paymentStatus == null ? "" : paymentStatus.toUpperCase(Locale.ROOT);

        Status verdict;
        if (statusCode != null && statusCode == 0 && "APPROVED".equals(upper)) {
            verdict = Status.PAID;
        } else if (statusCode != null && (statusCode == 3 || statusCode == 4 || statusCode == 7)
                || "DECLINED".equals(upper) || "CANCELLED".equals(upper) || "REFUNDED".equals(upper)) {
            verdict = Status.FAILED;
        } else {
            // 2 PENDING, PRE-AUTH (only with type=pre-auth, which is never sent) and the unknown.
            verdict = Status.PENDING;
        }

        BigDecimal amount = decimal(first(data, "original_amount", "total_amount", "payment_amount"));
        String currency = text(first(data, "payment_currency", "original_currency"));
        String approvalCode = text(data.path("apv"));
        String cardMasked = verdict == Status.PAID ? cardMaskedOf(tranId) : null;
        return new CardTransactionResult(verdict, amount, currency, approvalCode, cardMasked,
                paymentStatus != null ? paymentStatus : message);
    }

    /**
     * Best effort: Get Transaction Details names the card brand and a masked account. Only the
     * brand and the last four digits are kept. Rate limited by PayWay (10/minute), so it is
     * only asked once a payment is approved; any failure just leaves the card unnamed.
     */
    private String cardMaskedOf(String tranId) {
        try {
            JsonNode root = post(PayWayHash.TRANSACTION_DETAIL_PATH, tranId);
            if (root == null) {
                return null;
            }
            JsonNode data = root.path("data");
            String brand = brandOf(text(data.path("payment_type")));
            if (brand == null) {
                return null;   // ABA Pay, KHQR, ... - not a card
            }
            String digits = text(data.path("payer_account"));
            digits = digits == null ? "" : digits.replaceAll("\\D", "");
            return digits.length() >= 4
                    ? brand + " •••• " + digits.substring(digits.length() - 4)
                    : brand;
        } catch (RuntimeException ex) {
            log.debug("Could not read card details of PayWay tran {}: {}", tranId,
                    ex.getClass().getSimpleName());
            return null;
        }
    }

    /** PayWay's payment_type for cards: VISA, MC, JCB, CUP (UnionPay). */
    private static String brandOf(String paymentType) {
        if (paymentType == null) {
            return null;
        }
        return switch (paymentType.trim().toUpperCase(Locale.ROOT)) {
            case "VISA" -> "VISA";
            case "MC", "MASTERCARD" -> "MASTERCARD";
            case "JCB" -> "JCB";
            case "CUP", "UNIONPAY" -> "UNIONPAY";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ plumbing

    /** POSTs {req_time, merchant_id, tran_id, hash} as JSON; null when PayWay sent nothing. */
    private JsonNode post(String path, String tranId) {
        String reqTime = PayWayHash.reqTime(Instant.now());
        String merchantId = config.merchantId().trim();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("req_time", reqTime);
        body.put("merchant_id", merchantId);
        body.put("tran_id", tranId);
        body.put("hash", PayWayHash.transactionHash(reqTime, merchantId, tranId, config.apiKey()));

        String text;
        try {
            text = restClient.post()
                    .uri(baseUrl() + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    // PayWay explains errors in the JSON body; read it rather than throw.
                    .onStatus(HttpStatusCode::isError, (request, response) ->
                            log.debug("PayWay {} returned HTTP {}", path, response.getStatusCode()))
                    .body(String.class);
        } catch (RestClientException ex) {
            log.error("PayWay {} is unreachable: {}", path, ex.getMessage());
            throw new ApiException("Could not reach ABA PayWay. Please try again in a moment.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return json.readTree(text);
        } catch (JacksonException ex) {
            log.warn("PayWay {} answered something that is not JSON", path);
            return null;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException("Card payment is not available yet",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String baseUrl() {
        String base = config == null ? null : blankToNull(config.baseUrl());
        return trimTrailingSlash(base == null ? DEFAULT_BASE_URL : base.trim());
    }

    private static void put(Map<String, String> fields, String name, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(name, value);
        }
    }

    private static JsonNode first(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return node.path(names[0]);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isValueNode()) {
            return null;
        }
        return blankToNull(node.asString());
    }

    private static Integer integer(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = text(node);
        try {
            return value == null ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
