package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.config.DeliveryOptionSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.NotificationRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * In-app notifications around an order: admins hear about new orders (and how the customer
 * pays), online payments and customer cancellations; the customer hears when the store moves
 * their order on and when their payment lands. Plus the bell's own endpoints: list, unread
 * count, mark read, mark all read - each user seeing only their own.
 * <p>
 * The class has a database of its own; {@link #setUp()} clears orders and notifications.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class NotificationIntegrationTest {

    private static final String API = "/api/v1/notifications";
    private static final String CHECKOUT = "/api/v1/orders/checkout";
    private static final String PICKUP = "Pick up at store";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DeliveryOptionSeeder deliveryOptionSeeder;
    @Autowired private DeliveryOptionRepository deliveryOptionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asCustomer;
    private User customer;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        notificationRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        deliveryOptionRepository.deleteAllInBatch();
        deliveryOptionSeeder.seedDefaults();

        User admin = userRepository.findByEmail("admin@test.local").orElseThrow();
        asAdmin = user(new CustomUserDetails(admin));
        customer = newUser(RoleName.ROLE_CUSTOMER, true);
        asCustomer = user(new CustomUserDetails(customer));
        productId = createProduct();
    }

    // ------------------------------------------------------------------ admin side

    @Test
    void aPickupOrderPaidAtTheStoreNotifiesEveryEnabledAdmin() throws Exception {
        User secondAdmin = newUser(RoleName.ROLE_ADMIN, true);
        User disabledAdmin = newUser(RoleName.ROLE_ADMIN, false);

        JsonNode order = placePickupOrder("CASH_ON_DELIVERY");

        JsonNode mine = list(asAdmin).get("content");
        assertThat(mine).hasSize(1);
        JsonNode n = mine.get(0);
        assertThat(n.get("type").asText()).isEqualTo("NEW_ORDER");
        assertThat(n.get("orderId").asLong()).isEqualTo(order.get("id").asLong());
        assertThat(n.get("orderNumber").asText()).isEqualTo(order.get("orderNumber").asText());
        assertThat(n.get("paymentMethod").asText()).isEqualTo("CASH_ON_DELIVERY");
        assertThat(n.get("deliveryType").asText()).isEqualTo(DeliveryType.PICKUP.name());
        assertThat(n.get("orderStatus").asText()).isEqualTo("PENDING");
        assertThat(n.get("actorName").asText()).isEqualTo("Sok Dara");
        assertThat(n.get("amount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(n.get("link").asText()).isEqualTo("/admin/orders/" + order.get("id").asLong());
        assertThat(n.get("title").asText()).isEqualTo("New order #" + order.get("orderNumber").asText());
        assertThat(n.get("message").asText()).contains("Sok Dara", "$50.00", "pays cash at the store");
        assertThat(n.get("read").asBoolean()).isFalse();

        assertThat(unread(asAdmin)).isEqualTo(1);
        assertThat(unread(user(new CustomUserDetails(secondAdmin)))).isEqualTo(1);
        assertThat(unread(user(new CustomUserDetails(disabledAdmin)))).isZero();
        // The customer placed it - nothing for them to be told.
        assertThat(unread(asCustomer)).isZero();
    }

    @Test
    void anOnlineOrderSaysTheCustomerPaysOnline() throws Exception {
        placePickupOrder("KHQR");

        JsonNode n = list(asAdmin).get("content").get(0);
        assertThat(n.get("paymentMethod").asText()).isEqualTo("KHQR");
        assertThat(n.get("message").asText()).contains("paying online (KHQR)");
    }

    @Test
    void aCustomerCancellingTellsTheAdmins() throws Exception {
        long orderId = placePickupOrder("CASH_ON_DELIVERY").get("id").asLong();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk());

        JsonNode latest = list(asAdmin).get("content").get(0);
        assertThat(latest.get("type").asText()).isEqualTo("ORDER_CANCELLED");
        assertThat(latest.get("orderStatus").asText()).isEqualTo("CANCELLED");
        assertThat(unread(asAdmin)).isEqualTo(2);
        assertThat(unread(asCustomer)).isZero();
    }

    @Test
    void aRefusedCheckoutLeavesNoNotification() throws Exception {
        mockMvc.perform(delete("/api/v1/cart").with(asCustomer)).andExpect(status().isOk());

        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(pickupBody("CASH_ON_DELIVERY"))))
                .andExpect(status().isBadRequest());

        assertThat(notificationRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ customer side

    @Test
    void confirmingAnOrderTellsTheCustomer() throws Exception {
        long orderId = placePickupOrder("CASH_ON_DELIVERY").get("id").asLong();

        setStatus(orderId, "CONFIRMED");

        JsonNode mine = list(asCustomer).get("content");
        assertThat(mine).hasSize(1);
        JsonNode n = mine.get(0);
        assertThat(n.get("type").asText()).isEqualTo("ORDER_UPDATED");
        assertThat(n.get("orderStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(n.get("title").asText()).isEqualTo("Order confirmed");
        assertThat(n.get("message").asText()).contains("pay in cash when you collect it");
        assertThat(n.get("link").asText()).isEqualTo("/orders/" + orderId);
        assertThat(n.has("actorName") && !n.get("actorName").isNull()).isFalse();
        // The admin did it; their bell still only holds the new-order notification.
        assertThat(unread(asAdmin)).isEqualTo(1);
    }

    @Test
    void everyStoreStatusChangeIsAnnouncedInPickupWords() throws Exception {
        long orderId = placePickupOrder("CASH_ON_DELIVERY").get("id").asLong();

        setStatus(orderId, "CONFIRMED");
        setStatus(orderId, "PROCESSING");
        setStatus(orderId, "SHIPPED");
        setStatus(orderId, "DELIVERED");
        // Saving the same status again changes nothing and says nothing.
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DELIVERED"))));

        List<String> titles = new ArrayList<>();
        list(asCustomer).get("content").forEach(n -> titles.add(n.get("title").asText()));
        assertThat(titles).containsExactly(
                "Order collected", "Ready for pickup", "Preparing your order", "Order confirmed");
        assertThat(unread(asCustomer)).isEqualTo(4);
    }

    @Test
    void theStoreCancellingTellsTheCustomer() throws Exception {
        long orderId = placePickupOrder("CASH_ON_DELIVERY").get("id").asLong();

        setStatus(orderId, "CANCELLED");

        JsonNode n = list(asCustomer).get("content").get(0);
        assertThat(n.get("orderStatus").asText()).isEqualTo("CANCELLED");
        assertThat(n.get("title").asText()).isEqualTo("Order cancelled");
    }

    @Test
    void aManuallyVerifiedPaymentTellsTheCustomerButNotTheAdmins() throws Exception {
        JsonNode order = placePickupOrder("KHQR");
        long paymentId = order.get("paymentId").asLong();

        mockMvc.perform(patch("/api/v1/admin/payments/" + paymentId + "/verify").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("transactionId", "TX-1"))))
                .andExpect(status().isOk());

        JsonNode n = list(asCustomer).get("content").get(0);
        assertThat(n.get("type").asText()).isEqualTo("PAYMENT_RECEIVED");
        assertThat(n.get("orderStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(n.get("message").asText()).contains("$50.00", "confirmed");
        // Only the new-order notification - the admin verified it themselves.
        assertThat(unread(asAdmin)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ the bell

    @Test
    void markingReadOneByOneAndAllAtOnce() throws Exception {
        placePickupOrder("CASH_ON_DELIVERY");
        addToCart(productId, 1);
        placePickupOrder("KHQR");
        assertThat(unread(asAdmin)).isEqualTo(2);

        long first = list(asAdmin).get("content").get(1).get("id").asLong();
        mockMvc.perform(patch(API + "/" + first + "/read").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        // Twice is fine.
        mockMvc.perform(patch(API + "/" + first + "/read").with(asAdmin)).andExpect(status().isOk());
        assertThat(unread(asAdmin)).isEqualTo(1);

        mockMvc.perform(patch(API + "/read-all").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        assertThat(unread(asAdmin)).isZero();
        list(asAdmin).get("content").forEach(n -> assertThat(n.get("read").asBoolean()).isTrue());
    }

    @Test
    void theListIsNewestFirstAndPaged() throws Exception {
        long orderId = placePickupOrder("CASH_ON_DELIVERY").get("id").asLong();
        setStatus(orderId, "CONFIRMED");
        setStatus(orderId, "PROCESSING");

        JsonNode page = data(mockMvc.perform(get(API).param("size", "1").with(asCustomer))
                .andExpect(status().isOk()));
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("orderStatus").asText()).isEqualTo("PROCESSING");
    }

    @Test
    void nobodySeesOrTouchesSomeoneElsesNotifications() throws Exception {
        placePickupOrder("CASH_ON_DELIVERY");
        long adminsOwn = list(asAdmin).get("content").get(0).get("id").asLong();

        assertThat(list(asCustomer).get("content")).isEmpty();
        mockMvc.perform(patch(API + "/" + adminsOwn + "/read").with(asCustomer))
                .andExpect(status().isNotFound());
        assertThat(unread(asAdmin)).isEqualTo(1);
    }

    @Test
    void signedOutVisitorsGetNothing() throws Exception {
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(API + "/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(API + "/read-all")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode placePickupOrder(String paymentMethod) throws Exception {
        return data(mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(pickupBody(paymentMethod))))
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> pickupBody(String paymentMethod) {
        long pickup = deliveryOptionRepository.findAll().stream()
                .filter(option -> option.getName().equals(PICKUP))
                .findFirst().orElseThrow().getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deliveryOptionId", pickup);
        body.put("paymentMethod", paymentMethod);
        body.put("contactName", "Sok Dara");
        body.put("contactPhone", "012345678");
        return body;
    }

    private void setStatus(long orderId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private JsonNode list(RequestPostProcessor who) throws Exception {
        return data(mockMvc.perform(get(API).with(who)).andExpect(status().isOk()));
    }

    private long unread(RequestPostProcessor who) throws Exception {
        return data(mockMvc.perform(get(API + "/unread-count").with(who)).andExpect(status().isOk()))
                .get("count").asLong();
    }

    /** 25.00 each, 10 in stock; two of them go into the customer's cart. */
    private long createProduct() throws Exception {
        int n = SEQ.incrementAndGet();
        long categoryId = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Notify cat " + n))))
                .andExpect(status().isCreated())).get("id").asLong();
        long id = data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", categoryId, "name", "Speaker " + n,
                                "sku", "SKU-NOTIFY-" + n, "price", new BigDecimal("25.00"), "stockQuantity", 10))))
                .andExpect(status().isCreated())).get("id").asLong();
        addToCart(id, 2);
        return id;
    }

    private void addToCart(long product, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", product, "quantity", quantity))))
                .andExpect(status().isOk());
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private User newUser(RoleName role, boolean enabled) {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Notify User " + n)
                .email("notify-user" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(enabled)
                .build();
        user.addRole(roleRepository.findByName(role).orElseThrow());
        return userRepository.save(user);
    }
}
