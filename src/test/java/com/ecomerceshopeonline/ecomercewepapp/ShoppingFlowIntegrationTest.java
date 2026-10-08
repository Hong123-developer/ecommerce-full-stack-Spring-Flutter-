package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.Product;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart -> checkout -> KHQR -> Bakong confirmation -> admin dashboard, through the real
 * filter chain and a real database.
 * <p>
 * The Bakong settings are pinned here on purpose: {@code ./config/application.properties}
 * outranks {@code src/test/resources}, so without these the suite would pick up whatever
 * real merchant account the developer has configured locally.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shopping_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class ShoppingFlowIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
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
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void cartToCheckoutToKhqrToConfirmedOrder() throws Exception {
        long categoryId = createCategory("Electronics");
        long productId = createProduct(categoryId, "Headphones", "SKU-HP-" + SEQ.incrementAndGet(),
                "25.00", 10);
        long addressId = createAddress();

        // --- add 2 to cart ---
        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":2}".formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(50.00))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.checkoutReady").value(true));

        // --- checkout: 50.00 goods + 1.50 delivery ---
        String orderBody = mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"KHQR\"}".formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.subtotal").value(50.00))
                .andExpect(jsonPath("$.data.totalAmount").value(51.50))
                .andExpect(jsonPath("$.data.orderNumber").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(orderBody).get("data").get("id").asLong();

        // stock was reserved during checkout
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(8);

        // the cart is emptied
        mockMvc.perform(get("/api/v1/cart").with(asCustomer))
                .andExpect(jsonPath("$.data.itemCount").value(0));

        // --- KHQR is minted ---
        String payBody = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.khqr.payload").isNotEmpty())
                .andExpect(jsonPath("$.data.khqr.md5").isNotEmpty())
                .andExpect(jsonPath("$.data.khqr.bankName").value("Test Bank"))
                .andReturn().getResponse().getContentAsString();

        var khqr = objectMapper.readTree(payBody).get("data").get("khqr");
        assertThat(khqr.get("payload").asString()).startsWith("000201010212");
        assertThat(khqr.get("qrImage").asString()).startsWith("data:image/png;base64,");
        assertThat(khqr.get("md5").asString()).hasSize(32);
        assertThat(khqr.get("expiresAt").asString()).endsWith("Z");
        assertThat(khqr.get("expiresInSeconds").asLong()).isBetween(1L, 30L * 60);

        long paymentId = objectMapper.readTree(payBody).get("data").get("id").asLong();

        // --- Bakong says it is still unpaid ---
        when(bakongClient.checkTransaction(anyString()))
                .thenReturn(BakongClient.TransactionCheck.pending("Awaiting payment"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

        // --- Bakong confirms ---
        when(bakongClient.checkTransaction(anyString())).thenReturn(
                new BakongClient.TransactionCheck(true, "HASH123", "payer@abaa", "Confirmed"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.transactionId").value("HASH123"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());

        // paying moves the order forward on its own
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                // money has been taken: cancelling now needs a refund through the shop
                .andExpect(jsonPath("$.data.cancellable").value(false));

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already been paid")));

        // --- the dashboard runs its grouped/CAST queries against real data ---
        mockMvc.perform(get("/api/v1/admin/dashboard").with(asAdmin).param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue.total").isNumber())
                .andExpect(jsonPath("$.data.revenue.paidCount").isNumber())
                .andExpect(jsonPath("$.data.orders.byStatus.CONFIRMED").isNumber())
                .andExpect(jsonPath("$.data.topProducts[0].productName").value("Headphones"))
                .andExpect(jsonPath("$.data.revenueByDay").isArray())
                .andExpect(jsonPath("$.data.registrationsByDay").isArray());

        mockMvc.perform(get("/api/v1/admin/customers/registrations").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    // ------------------------------------------------------------------ stock

    @Test
    void cancellingAnOrderPutsStockBack() throws Exception {
        long categoryId = createCategory("Cancels");
        long productId = createProduct(categoryId, "Mouse", "SKU-MS-" + SEQ.incrementAndGet(),
                "10.00", 5);
        long addressId = createAddress();

        addToCart(productId, 3);
        long orderId = checkout(addressId, "CASH_ON_DELIVERY");
        assertThat(stockOf(productId)).isEqualTo(2);

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));

        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    void paymentArrivingAfterCancellationIsNotSettled() throws Exception {
        long categoryId = createCategory("Late Pay");
        long productId = createProduct(categoryId, "Lamp", "SKU-LAMP-" + SEQ.incrementAndGet(),
                "12.00", 4);
        long addressId = createAddress();

        addToCart(productId, 1);
        long orderId = checkout(addressId, "KHQR");

        String payBody = mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(payBody).get("data").get("id").asLong();

        // The shopper cancels while the QR is still open...
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk());

        // ...then pays it anyway.
        when(bakongClient.checkTransaction(anyString())).thenReturn(
                new BakongClient.TransactionCheck(true, "LATE-HASH", "payer@abaa", "Confirmed"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/check").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(stockOf(productId)).isEqualTo(4);
    }

    @Test
    void checkoutRefusesWhenStockRanOutAfterTheItemWasAddedToTheCart() throws Exception {
        long categoryId = createCategory("Races");
        long productId = createProduct(categoryId, "Last One", "SKU-LAST-" + SEQ.incrementAndGet(),
                "10.00", 2);
        long addressId = createAddress();

        addToCart(productId, 2);

        // Someone else buys one before this shopper reaches checkout.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStockQuantity(1);
        productRepository.save(product);

        mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"KHQR\"}".formatted(addressId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not enough stock")));

        // the failed checkout must not have eaten the remaining unit
        assertThat(stockOf(productId)).isEqualTo(1);
    }

    @Test
    void cartRejectsMoreThanIsInStock() throws Exception {
        long categoryId = createCategory("Limited");
        long productId = createProduct(categoryId, "Rare", "SKU-RARE-" + SEQ.incrementAndGet(),
                "10.00", 1);

        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":5}".formatted(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("left in stock")));
    }

    @Test
    void emptyCartCannotCheckOut() throws Exception {
        long addressId = createAddress();
        mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"KHQR\"}".formatted(addressId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));
    }

    // ------------------------------------------------------------------ isolation

    @Test
    void oneShopperCannotSeeAnothersOrder() throws Exception {
        long categoryId = createCategory("Private");
        long productId = createProduct(categoryId, "Thing", "SKU-PRIV-" + SEQ.incrementAndGet(),
                "10.00", 5);
        long addressId = createAddress();
        addToCart(productId, 1);
        long orderId = checkout(addressId, "CASH_ON_DELIVERY");

        RequestPostProcessor otherShopper = user(new CustomUserDetails(newCustomer()));

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(otherShopper))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/orders/" + orderId).with(otherShopper))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCannotReachTheAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").with(asCustomer))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Shopper " + n)
                .email("shopper" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }

    private long createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s-%d\"}".formatted(name, SEQ.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private long createProduct(long categoryId, String name, String sku,
                               String price, int stock) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"%s","sku":"%s","price":%s,"stockQuantity":%d}
                                """.formatted(categoryId, name, sku, price, stock)))
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
                .andExpect(jsonPath("$.data.isDefault").value(true))
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
        String body = mockMvc.perform(checkoutRequest(addressId, method))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private MockHttpServletRequestBuilder checkoutRequest(long addressId, String method) {
        return post("/api/v1/orders/checkout").with(asCustomer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":%d,\"paymentMethod\":\"%s\"}".formatted(addressId, method));
    }

    private int stockOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }
}
