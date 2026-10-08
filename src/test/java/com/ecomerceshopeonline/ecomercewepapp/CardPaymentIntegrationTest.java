package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.PaymentStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import com.ecomerceshopeonline.ecomercewepapp.service.PaymentService;
import com.ecomerceshopeonline.ecomercewepapp.service.impl.PayWayCardGateway;
import com.ecomerceshopeonline.ecomercewepapp.util.PayWayHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Card payments through ABA PayWay's hosted page, with PayWay's HTTP API replaced by a
 * {@link MockRestServiceServer}: the real gateway signs the form and parses the answers.
 * <p>
 * Every {@code app.payway.*} (and Bakong) value is pinned here because
 * {@code ./config/application.properties} outranks the test resources.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:card_payment_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30",
        "app.payway.base-url=" + CardPaymentIntegrationTest.BASE,
        "app.payway.merchant-id=" + CardPaymentIntegrationTest.MERCHANT,
        "app.payway.api-key=" + CardPaymentIntegrationTest.API_KEY,
        "app.payway.callback-url=" + CardPaymentIntegrationTest.CALLBACK,
        "app.payway.frontend-url=http://localhost:5173",
        "app.payway.lifetime-minutes=30"
})
class CardPaymentIntegrationTest {

    static final String BASE = "https://checkout-sandbox.payway.test";
    static final String MERCHANT = "ec_test_01";
    static final String API_KEY = "sk-TEST-payway-secret-0123456789";
    static final String CALLBACK = "https://api.shop.test/api/v1/payments/payway/callback";
    private static final String CHECK_URL = BASE + PayWayHash.CHECK_TRANSACTION_PATH;
    private static final String DETAIL_URL = BASE + PayWayHash.TRANSACTION_DETAIL_PATH;
    private static final String CALLBACK_PATH = "/api/v1/payments/payway/callback";

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** The real PayWay gateway, its RestClient bound to a mock server instead of PayWay. */
    @TestConfiguration
    static class MockPayWay {
        static final RestClient.Builder BUILDER = RestClient.builder();
        static final MockRestServiceServer SERVER =
                MockRestServiceServer.bindTo(BUILDER).ignoreExpectOrder(true).build();

        @Bean
        @Primary
        CardPaymentGateway mockedPayWay(AppProperties properties) {
            return new PayWayCardGateway(properties, BUILDER);
        }
    }

    private final MockRestServiceServer payWay = MockPayWay.SERVER;

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private RequestPostProcessor asCustomer;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        asCustomer = user(new CustomUserDetails(newCustomer()));
        payWay.reset();
    }

    // ------------------------------------------------------------------ methods

    @Test
    void methodsListsCardOnceConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/payments/methods"))   // no login needed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("KHQR"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[1].code").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.data[1].enabled").value(true))
                .andExpect(jsonPath("$.data[2].code").value("CARD"))
                .andExpect(jsonPath("$.data[2].enabled").value(true))
                .andExpect(jsonPath("$.data[2].provider").value("ABA PayWay"))
                .andExpect(jsonPath("$.data[2].brands[0]").value("VISA"))
                .andExpect(jsonPath("$.data[2].brands[1]").value("MASTERCARD"))
                .andExpect(jsonPath("$.data[2].brands[2]").value("UNIONPAY"))
                .andExpect(jsonPath("$.data[2].brands[3]").value("JCB"));
    }

    // ------------------------------------------------------------------ pay

    @Test
    void payReturnsASignedFormForTheOrderTotalFromTheDatabase() throws Exception {
        long productId = createProduct("25.00", 10);
        long orderId = placeCardOrder(productId, 2);
        assertThat(stockOf(productId)).isEqualTo(8);   // reserved like KHQR

        String body = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.gatewayProvider").value("ABA PayWay"))
                .andExpect(jsonPath("$.data.cardCheckout.method").value("POST"))
                .andExpect(jsonPath("$.data.cardCheckout.action")
                        .value(BASE + "/api/payment-gateway/v1/payments/purchase"))
                .andExpect(jsonPath("$.data.khqr").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(API_KEY);

        JsonNode data = objectMapper.readTree(body).get("data");
        JsonNode fields = data.get("cardCheckout").get("fields");
        Order order = orderRepository.findById(orderId).orElseThrow();
        String tranId = fields.get("tran_id").asString();

        assertThat(fields.get("amount").asString())
                .isEqualTo(order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertThat(fields.get("merchant_id").asString()).isEqualTo(MERCHANT);
        assertThat(fields.get("currency").asString()).isEqualTo("USD");
        assertThat(fields.get("payment_option").asString()).isEqualTo("cards");
        assertThat(fields.get("type").asString()).isEqualTo("purchase");
        assertThat(fields.get("lifetime").asString()).isEqualTo("30");
        assertThat(fields.get("req_time").asString()).matches("\\d{14}");
        assertThat(tranId).matches("\\d{11,20}").startsWith(String.valueOf(orderId));
        assertThat(data.get("gatewayTranId").asString()).isEqualTo(tranId);
        assertThat(fields.get("return_url").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(CALLBACK.getBytes(StandardCharsets.UTF_8)));
        assertThat(fields.get("continue_success_url").asString())
                .isEqualTo("http://localhost:5173/payment/" + orderId + "?from=card");
        assertThat(fields.get("return_params").asString()).isEqualTo(order.getOrderNumber());
        String items = new String(Base64.getDecoder().decode(fields.get("items").asString()),
                StandardCharsets.UTF_8);
        assertThat(items).contains("\"quantity\":2").contains("\"price\":25.00");

        // Posted in the documented order, hash last; the hash recomputed independently.
        List<String> names = new ArrayList<>(fields.propertyNames());
        assertThat(names.getLast()).isEqualTo("hash");
        List<String> hashed = names.subList(0, names.size() - 1);
        assertThat(hashed).isEqualTo(PayWayHash.PURCHASE_HASH_FIELDS.stream()
                .filter(hashed::contains).toList());
        StringBuilder b4hash = new StringBuilder();
        hashed.forEach(name -> b4hash.append(fields.get(name).asString()));
        assertThat(fields.get("hash").asString()).isEqualTo(hmac(b4hash.toString()));

        assertThat(paymentRepository.findByOrderIdWithOrder(orderId).orElseThrow().getGatewayTranId())
                .isEqualTo(tranId);
    }

    // ------------------------------------------------------------------ check

    @Test
    void checkSettlesAnApprovedPaymentAndConfirmsTheOrder() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 2);
        JsonNode paid = startCardPayment(orderId);
        long paymentId = paid.get("id").asLong();
        String tranId = paid.get("gatewayTranId").asString();

        // Still on PayWay's page.
        answerCheck(tranId, pending(tranId));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

        payWay.reset();
        answerCheck(tranId, approved(tranId, totalOf(orderId), "USD"));
        answerDetail(tranId);
        String body = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.transactionId").value(tranId))
                .andExpect(jsonPath("$.data.gatewayTranId").value(tranId))
                .andExpect(jsonPath("$.data.gatewayApprovalCode").value("753786"))
                .andExpect(jsonPath("$.data.cardMasked").value("VISA •••• 4242"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(API_KEY);

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.cancellable").value(false));

        // Paid: no new attempt, same rule as KHQR.
        mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This order is already paid"));

        // The admin views carry the card fields too.
        String admin = mockMvc.perform(get("/api/v1/admin/payments/" + paymentId).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gatewayProvider").value("ABA PayWay"))
                .andExpect(jsonPath("$.data.cardMasked").value("VISA •••• 4242"))
                .andExpect(jsonPath("$.data.customer.email").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).doesNotContain(API_KEY);
    }

    @Test
    void anApprovalForAnotherAmountOrCurrencyIsNotSettled() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 2);
        JsonNode started = startCardPayment(orderId);
        long paymentId = started.get("id").asLong();
        String tranId = started.get("gatewayTranId").asString();

        answerCheck(tranId, approved(tranId, "1.00", "USD"));
        answerDetail(tranId);
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

        payWay.reset();
        answerCheck(tranId, approved(tranId, totalOf(orderId), "KHR"));
        answerDetail(tranId);
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

        Payment payment = paymentRepository.findByOrderIdWithOrder(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionId()).isNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void aFailedAttemptCanBeRetriedWithANewTranId() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 1);
        JsonNode first = startCardPayment(orderId);
        long paymentId = first.get("id").asLong();
        String firstTran = first.get("gatewayTranId").asString();

        answerCheck(firstTran, declined(firstTran));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // "Try again": the declined attempt is re-checked, then a fresh one is signed.
        String body = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.cardCheckout.fields.hash").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode retry = objectMapper.readTree(body).get("data");
        String secondTran = retry.get("cardCheckout").get("fields").get("tran_id").asString();
        assertThat(secondTran).isNotEqualTo(firstTran).startsWith(String.valueOf(orderId));
        assertThat(retry.get("gatewayTranId").asString()).isEqualTo(secondTran);
    }

    // ------------------------------------------------------------------ callback

    @Test
    void callbackForAnUnknownTranIsAcknowledgedAndIgnored() throws Exception {
        // No expectations on the mock server: asking PayWay anything would fail the test.
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tran_id\":\"98765432101234567890\",\"status\":\"0\",\"apv\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(true));
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("tran_id", "NOT-OURS-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(true));
        mockMvc.perform(post(CALLBACK_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callbackSettlesFromTheProvidersAnswerAndRepeatsAreHarmless() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 2);
        JsonNode started = startCardPayment(orderId);
        String tranId = started.get("gatewayTranId").asString();

        answerCheck(tranId, approved(tranId, totalOf(orderId), "USD"));
        answerDetail(tranId);

        // The body claims nothing useful - only tran_id is read; PayWay is asked for the rest.
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tran_id\":\"%s\",\"status\":\"0\",\"original_amount\":0.01}".formatted(tranId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(true));

        Payment settled = paymentRepository.findByOrderIdWithOrder(orderId).orElseThrow();
        assertThat(settled.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(settled.getGatewayApprovalCode()).isEqualTo("753786");
        LocalDateTime paidAt = settled.getPaidAt();
        assertThat(paidAt).isNotNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // The same pushback again (form-encoded this time), and the shopper's poll: no change.
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("tran_id", tranId))
                .andExpect(status().isOk());
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("response", "{\"tran_id\":\"%s\"}".formatted(tranId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + settled.getId() + "/check").with(asCustomer))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        Payment again = paymentRepository.findByOrderIdWithOrder(orderId).orElseThrow();
        assertThat(again.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(again.getPaidAt()).isEqualTo(paidAt);
        assertThat(again.getTransactionId()).isEqualTo(tranId);
    }

    @Test
    void aLateCallbackForAnOlderAttemptStillSettles() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 1);
        String firstTran = startCardPayment(orderId).get("gatewayTranId").asString();

        // The shopper comes back and starts again while the first page is still open...
        answerCheck(firstTran, pending(firstTran));
        String body = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondTran = objectMapper.readTree(body).get("data").get("gatewayTranId").asString();
        assertThat(secondTran).isNotEqualTo(firstTran);

        // ...then completes the first one after all.
        payWay.reset();
        answerCheck(firstTran, approved(firstTran, totalOf(orderId), "USD"));
        answerDetail(firstTran);
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tran_id\":\"%s\"}".formatted(firstTran)))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByOrderIdWithOrder(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getGatewayTranId()).isEqualTo(firstTran);
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void anUnpaidCardOrderExpiresAndItsStockComesBack() throws Exception {
        long productId = createProduct("10.00", 5);
        long attempted = placeCardOrder(productId, 2);
        String tranId = startCardPayment(attempted).get("gatewayTranId").asString();
        long neverAttempted = placeCardOrder(productId, 1);
        assertThat(stockOf(productId)).isEqualTo(2);

        // Within the window nothing happens.
        assertThat(paymentService.expireStaleCardPayments(LocalDateTime.now())).isZero();

        // Past it, every still-open attempt is asked about first (PayWay: still pending).
        payWay.expect(ExpectedCount.manyTimes(), requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(pending(tranId), MediaType.APPLICATION_JSON));
        assertThat(paymentService.expireStaleCardPayments(LocalDateTime.now().plusMinutes(36)))
                .isGreaterThanOrEqualTo(2);

        for (long orderId : List.of(attempted, neverAttempted)) {
            mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.paymentStatus").value("EXPIRED"));
        }
        assertThat(stockOf(productId)).isEqualTo(5);

        // Expired is final: no new attempt, and money arriving now is not taken as payment.
        mockMvc.perform(post("/api/v1/payments/orders/" + attempted).with(asCustomer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This order is already expired"));
        payWay.reset();
        answerCheck(tranId, approved(tranId, totalOf(attempted), "USD"));
        answerDetail(tranId);
        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tran_id\":\"%s\"}".formatted(tranId)))
                .andExpect(status().isOk());
        assertThat(paymentRepository.findByOrderIdWithOrder(attempted).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void deliveringAnUnpaidCardOrderDoesNotMarkItPaid() throws Exception {
        long orderId = placeCardOrder(createProduct("25.00", 10), 1);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
    }

    @Test
    void otherPaymentsSerialiseTheCardFieldsAsNull() throws Exception {
        long productId = createProduct("10.00", 5);
        long addressId = createAddress();
        addToCart(productId, 1);
        long orderId = checkout(addressId, "CASH_ON_DELIVERY");

        String body = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardCheckout").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        for (String field : List.of("gatewayProvider", "gatewayTranId", "gatewayApprovalCode", "cardMasked")) {
            assertThat(data.has(field)).as(field).isTrue();
            assertThat(data.get(field).isNull()).as(field).isTrue();
        }
    }

    // ------------------------------------------------------------------ PayWay answers

    private void answerCheck(String tranId, String json) {
        payWay.expect(ExpectedCount.manyTimes(), requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode sent = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(sent.get("tran_id").asString()).isEqualTo(tranId);
                    assertThat(sent.get("merchant_id").asString()).isEqualTo(MERCHANT);
                    assertThat(sent.get("hash").asString()).isEqualTo(hmac(sent.get("req_time").asString()
                            + sent.get("merchant_id").asString() + sent.get("tran_id").asString()));
                })
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void answerDetail(String tranId) {
        payWay.expect(ExpectedCount.manyTimes(), requestTo(DETAIL_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"payment_status_code":0,"payment_status":"APPROVED","payment_type":"VISA",
                                 "payer_account":"424242xxxxxx4242","card_source":"OFFUS_INTERNATIONAL"},
                         "status":{"code":"00","message":"Success!","tran_id":"%s"}}
                        """.formatted(tranId), MediaType.APPLICATION_JSON));
    }

    private static String approved(String tranId, String amount, String currency) {
        return """
                {"data":{"payment_status_code":0,"total_amount":%s,"original_amount":%s,"refund_amount":0,
                         "discount_amount":0,"payment_amount":%s,"payment_currency":"%s","apv":"753786",
                         "payment_status":"APPROVED","transaction_date":"2026-10-07 13:55:25"},
                 "status":{"code":"00","message":"Success!","tran_id":"%s"}}
                """.formatted(amount, amount, amount, currency, tranId);
    }

    private static String pending(String tranId) {
        return """
                {"data":{"payment_status_code":2,"payment_status":"PENDING","total_amount":0,
                         "payment_currency":"USD"},
                 "status":{"code":"00","message":"Success!","tran_id":"%s"}}
                """.formatted(tranId);
    }

    private static String declined(String tranId) {
        return """
                {"data":{"payment_status_code":3,"payment_status":"DECLINED","payment_currency":"USD"},
                 "status":{"code":"00","message":"Success!","tran_id":"%s"}}
                """.formatted(tranId);
    }

    private static String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ shop helpers

    private long placeCardOrder(long productId, int quantity) throws Exception {
        long addressId = createAddress();
        addToCart(productId, quantity);
        return checkout(addressId, "CARD");
    }

    private JsonNode startCardPayment(long orderId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardCheckout.fields.tran_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private String totalOf(long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getTotalAmount()
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Card Shopper " + n)
                .email("card" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }

    private long createProduct(String price, int stock) throws Exception {
        String category = mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cards-%d\"}".formatted(SEQ.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(category).get("data").get("id").asLong();
        String body = mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"Headphones","sku":"SKU-CARD-%d","price":%s,"stockQuantity":%d}
                                """.formatted(categoryId, SEQ.incrementAndGet(), price, stock)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private long createAddress() throws Exception {
        String body = mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"Sok Dara","phone":"012345678",
                                 "province":"Phnom Penh","district":"Chamkarmon",
                                 "addressDetail":"No. 12, St. 271"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private void addToCart(long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private long checkout(long addressId, String method) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"%s\"}".formatted(addressId, method)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private int stockOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }
}
