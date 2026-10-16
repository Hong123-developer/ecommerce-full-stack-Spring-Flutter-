package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.config.DeliveryOptionSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin › Settings › Data: backup download, restore and the per-group delete, against a
 * database of its own holding a little of everything (a variant product with photos, a banner,
 * branding, a customer with an address, a cart and a pickup order with its payment and
 * notifications).
 */
@SpringBootTest(properties = {
        // Set DATA_TEST_DB_URL (+ _DRIVER, _USER, _PASSWORD) to run against a throwaway PostgreSQL.
        "spring.datasource.url=${DATA_TEST_DB_URL:jdbc:h2:mem:data_management_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${DATA_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${DATA_TEST_DB_USER:sa}",
        "spring.datasource.password=${DATA_TEST_DB_PASSWORD:}",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class DataManagementIntegrationTest {

    private static final String DATA = "/api/v1/admin/data";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DeliveryOptionSeeder deliveryOptionSeeder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DeliveryOptionRepository deliveryOptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asCustomer;
    private User customer;
    private long orderId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        asAdmin = user(new CustomUserDetails(admin));

        // A clean slate with only the seeded admin, then a little of everything.
        delete(Set.of("ORDERS", "CUSTOMERS", "CATALOG", "CONTENT", "SETTINGS")).andExpect(status().isOk());
        deliveryOptionSeeder.seedDefaults();
        customer = newUser(RoleName.ROLE_CUSTOMER);
        asCustomer = user(new CustomUserDetails(customer));
        seedShop();
    }

    // ------------------------------------------------------------------ access

    @Test
    void onlyAnAdminWithTheirPasswordGetsIn() throws Exception {
        mockMvc.perform(get(DATA + "/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DATA + "/summary").with(asCustomer)).andExpect(status().isForbidden());
        mockMvc.perform(post(DATA + "/backup").with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", "Secret123")))).andExpect(status().isForbidden());

        mockMvc.perform(post(DATA + "/backup").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "wrong-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Incorrect password"));
        delete(Set.of("ORDERS"), "wrong-password", "DELETE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Incorrect password"));
        delete(Set.of("ORDERS"), adminPassword, "remove")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirm").value("Type DELETE to confirm"));

        assertThat(summary().get("orders").asInt()).isEqualTo(1);
    }

    @Test
    void theSummaryCountsEachGroup() throws Exception {
        JsonNode s = summary();
        assertThat(s.get("orders").asInt()).isEqualTo(1);
        assertThat(s.get("payments").asInt()).isEqualTo(1);
        assertThat(s.get("customers").asInt()).isEqualTo(1);
        assertThat(s.get("customersWithOrders").asInt()).isEqualTo(1);
        assertThat(s.get("products").asInt()).isEqualTo(1);
        assertThat(s.get("categories").asInt()).isEqualTo(1);
        assertThat(s.get("heroSlides").asInt()).isEqualTo(1);
        assertThat(s.get("deliveryOptions").asInt()).isEqualTo(4);
        assertThat(s.get("storeSettingsSaved").asBoolean()).isTrue();
        assertThat(s.get("admins").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ backup

    @Test
    void aBackupHoldsEveryTableWithItsIdsAndRolesByName() throws Exception {
        JsonNode backup = backup();

        assertThat(backup.get("format").asText()).isEqualTo("ecomercewepapp-backup");
        assertThat(backup.get("version").asInt()).isEqualTo(1);
        JsonNode tables = backup.get("tables");
        assertThat(tables.get("orders")).hasSize(1);
        assertThat(tables.get("orders").get(0).get("id").asLong()).isEqualTo(orderId);
        assertThat(tables.get("order_items")).hasSize(1);
        assertThat(tables.get("payments")).hasSize(1);
        assertThat(tables.get("product_variants")).hasSize(2);
        assertThat(tables.get("product_variant_options")).hasSize(2);
        assertThat(tables.get("product_option_names")).hasSize(1);
        assertThat(tables.get("product_gallery_images")).hasSize(1);
        assertThat(tables.get("hero_slides")).hasSize(1);
        assertThat(tables.get("store_settings")).hasSize(1);
        assertThat(tables.get("payment_account")).hasSize(1);
        assertThat(tables.get("payment_account").get(0).get("usd_account_number").asText())
                .isEqualTo("0001-00000000-01");
        assertThat(tables.get("addresses")).hasSize(1);
        assertThat(tables.get("notifications").size()).isPositive();
        // Exact money as text, never a rounded double.
        assertThat(tables.get("orders").get(0).get("total_amount").asText()).isEqualTo("25.00");
        List<String> roles = tables.get("user_roles").findValuesAsString("role_name");
        assertThat(roles).contains("ROLE_ADMIN", "ROLE_CUSTOMER");
        // Sign-in state stays out of a backup.
        assertThat(tables.has("refresh_tokens")).isFalse();
        assertThat(backup.get("counts").get("users").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletingOrdersKeepsTheCatalogueAndCustomers() throws Exception {
        delete(Set.of("ORDERS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").value(1))
                .andExpect(jsonPath("$.data.payments").value(1));

        JsonNode s = summary();
        assertThat(s.get("orders").asInt()).isZero();
        assertThat(s.get("notifications").asInt()).isZero();
        assertThat(s.get("customers").asInt()).isEqualTo(1);
        assertThat(s.get("products").asInt()).isEqualTo(1);
    }

    @Test
    void customersWithOrdersOnlyGoTogetherWithTheOrders() throws Exception {
        delete(Set.of("CUSTOMERS")).andExpect(status().isBadRequest());
        assertThat(summary().get("customers").asInt()).isEqualTo(1);

        delete(Set.of("CUSTOMERS", "ORDERS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users").value(1));

        JsonNode s = summary();
        assertThat(s.get("customers").asInt()).isZero();
        assertThat(s.get("admins").asInt()).isEqualTo(1);
        // The admin is untouched and can still sign in.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", adminPassword))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTheCatalogueLeavesPastOrdersReadable() throws Exception {
        delete(Set.of("CATALOG", "CONTENT")).andExpect(status().isOk());

        assertThat(summary().get("products").asInt()).isZero();
        assertThat(summary().get("deliveryOptions").asInt()).isZero();
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("Phone case"))
                .andExpect(jsonPath("$.data.items[0].productId").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryMethodName").value("Pick up at store"));
    }

    @Test
    void deletingEverythingLeavesOnlyTheAdmins() throws Exception {
        delete(Set.of("ORDERS", "CUSTOMERS", "CATALOG", "CONTENT", "SETTINGS")).andExpect(status().isOk());

        JsonNode s = summary();
        for (String field : List.of("orders", "payments", "notifications", "customers", "products",
                "categories", "heroSlides", "deliveryOptions")) {
            assertThat(s.get(field).asInt()).as(field).isZero();
        }
        assertThat(s.get("storeSettingsSaved").asBoolean()).isFalse();
        assertThat(s.get("admins").asInt()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/payment-account").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").doesNotExist());
    }

    // ------------------------------------------------------------------ restore

    @Test
    void restoreBringsBackExactlyWhatWasBackedUp() throws Exception {
        JsonNode before = backup();
        delete(Set.of("ORDERS", "CUSTOMERS", "CATALOG", "CONTENT", "SETTINGS")).andExpect(status().isOk());

        restore(before).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").value(1))
                .andExpect(jsonPath("$.data.users").value(2));

        JsonNode after = backup();
        assertThat(after.get("tables")).isEqualTo(before.get("tables"));
        // Links survived: the order still belongs to the customer and shows its product.
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("Phone case"));
    }

    @Test
    void idsAreNeverHandedOutTwice() throws Exception {
        JsonNode before = backup();
        User later = newUser(RoleName.ROLE_CUSTOMER);

        restore(before).andExpect(status().isOk());
        assertThat(userRepository.findById(later.getId())).isEmpty();

        User next = newUser(RoleName.ROLE_CUSTOMER);
        assertThat(next.getId()).isGreaterThan(later.getId());
        long newCategory = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "After restore"))))
                .andExpect(status().isCreated())).get("id").asLong();
        long maxBackedUp = before.get("tables").get("categories").findValues("id").stream()
                .mapToLong(JsonNode::asLong).max().orElse(0);
        assertThat(newCategory).isGreaterThan(maxBackedUp);
    }

    @Test
    void restoreSignsEveryoneOut() throws Exception {
        JsonNode before = backup();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", adminEmail, "password", adminPassword)))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class)).isPositive();

        restore(before).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class)).isZero();
    }

    @Test
    void aTokenOnlyWorksForTheAccountItWasIssuedTo() throws Exception {
        String token = data(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", customer.getEmail(), "password", "Secret123"))))
                .andExpect(status().isOk())).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // What a restore from another database can do: the same id now belongs to someone else.
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "someone-else@test.local", customer.getId());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBrokenBackupChangesNothing() throws Exception {
        ObjectNode broken = (ObjectNode) backup();
        // An item of an order the file doesn't have.
        ObjectNode item = (ObjectNode) broken.get("tables").get("order_items").get(0);
        item.put("order_id", 987654);

        restore(broken).andExpect(status().isBadRequest());

        assertThat(summary().get("orders").asInt()).isEqualTo(1);
        assertThat(summary().get("customers").asInt()).isEqualTo(1);
    }

    @Test
    void restoreRefusesFilesThatAreNotOurBackups() throws Exception {
        ObjectNode foreign = (ObjectNode) backup();
        foreign.put("format", "something-else");
        restore(foreign).andExpect(status().isBadRequest());

        ObjectNode newer = (ObjectNode) backup();
        newer.put("version", 99);
        restore(newer).andExpect(status().isBadRequest());

        ObjectNode noAdmin = (ObjectNode) backup();
        ArrayNode links = (ArrayNode) noAdmin.get("tables").get("user_roles");
        for (int i = links.size() - 1; i >= 0; i--) {
            if ("ROLE_ADMIN".equals(links.get(i).get("role_name").asText())) {
                links.remove(i);
            }
        }
        restore(noAdmin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrator")));

        mockMvc.perform(post(DATA + "/restore").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", adminPassword, "confirm", "nope", "backup", Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirm").value("Type RESTORE to confirm"));
        assertThat(summary().get("orders").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /** A variant product with a photo, a banner, branding, and a customer's pickup order. */
    private void seedShop() throws Exception {
        long categoryId = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Accessories"))))
                .andExpect(status().isCreated())).get("id").asLong();
        int n = SEQ.incrementAndGet();
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("categoryId", categoryId);
        product.put("name", "Phone case");
        product.put("sku", "CASE-" + n);
        product.put("price", new BigDecimal("12.50"));
        product.put("stockQuantity", 0);
        product.put("imageUrl", "http://localhost:8080/uploads/products/case.png");
        product.put("galleryUrls", List.of("http://localhost:8080/uploads/products/case-2.png"));
        product.put("optionNames", List.of("Color"));
        product.put("variants", List.of(
                Map.of("options", List.of("Black"), "sku", "CASE-" + n + "-BLK", "price", 12.5, "stockQuantity", 5),
                Map.of("options", List.of("Red"), "sku", "CASE-" + n + "-RED", "price", 12.5, "stockQuantity", 5)));
        JsonNode created = data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(product)))
                .andExpect(status().isCreated()));
        long variantId = created.get("variants").get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/hero-slides").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Big sale", "imageUrl", "http://localhost:8080/uploads/products/hero.png"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/admin/store-settings").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("storeName", "Nova Trend"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/payment-account").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankName", "ACLEDA Bank", "accountName", "Test Account Holder",
                                "usdAccountNumber", "0001-00000000-01", "khrAccountNumber", "0001-00000000-02"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/addresses").with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recipientName", "Sok Dara", "phone", "012345678",
                                "province", "Phnom Penh", "district", "Chamkarmon", "addressDetail", "No. 12"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", created.get("id").asLong(), "quantity", 2, "variantId", variantId))))
                .andExpect(status().isOk());
        long pickup = deliveryOptionRepository.findAll().stream()
                .filter(option -> option.getType() == DeliveryType.PICKUP)
                .findFirst().orElseThrow().getId();
        orderId = data(mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deliveryOptionId", pickup, "paymentMethod", "CASH_ON_DELIVERY",
                                "contactName", "Sok Dara", "contactPhone", "012345678"))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode summary() throws Exception {
        return data(mockMvc.perform(get(DATA + "/summary").with(asAdmin)).andExpect(status().isOk()));
    }

    private JsonNode backup() throws Exception {
        return data(mockMvc.perform(post(DATA + "/backup").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", adminPassword)))).andExpect(status().isOk()));
    }

    private ResultActions restore(JsonNode backup) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", adminPassword);
        body.put("confirm", "RESTORE");
        body.put("backup", backup);
        return mockMvc.perform(post(DATA + "/restore").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions delete(Set<String> groups) throws Exception {
        return delete(groups, adminPassword, "DELETE");
    }

    private ResultActions delete(Set<String> groups, String password, String confirm) throws Exception {
        return mockMvc.perform(post(DATA + "/delete").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", password, "confirm", confirm, "groups", groups))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private User newUser(RoleName role) {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Data User " + n)
                .email("data-user" + n + "-" + System.nanoTime() + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(role).orElseThrow());
        return userRepository.save(user);
    }
}
