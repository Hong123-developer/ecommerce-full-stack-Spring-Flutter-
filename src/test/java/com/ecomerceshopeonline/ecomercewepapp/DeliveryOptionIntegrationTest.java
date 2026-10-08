package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.config.DeliveryOptionSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delivery options ("how do you want to receive your order?") through the real filter chain:
 * the startup seed, the public checkout list, admin CRUD with validation, reorder and status,
 * the rule that one option always stays enabled, and that an option used by an order can
 * only be disabled.
 * <p>
 * The class has a database of its own, so every test starts from exactly the four seeded
 * options: {@link #setUp()} clears orders and options and runs the seeder again.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery_option_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class DeliveryOptionIntegrationTest {

    private static final String PUBLIC = "/api/v1/delivery-options";
    private static final String ADMIN = "/api/v1/admin/delivery-options";
    private static final String LAST_ENABLED =
            "At least one delivery option must stay enabled, or customers cannot check out";
    private static final String ORDER_ERROR = "Order must list every delivery option exactly once";
    private static final String LOGO_ERROR = "Logo URL must start with / or http(s)://";

    private static final String JT = "J&T Express";
    private static final String VET = "Virak Buntham Express (VET)";
    private static final String RIDER = "Store rider (Phnom Penh)";
    private static final String PICKUP = "Pick up at store";

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DeliveryOptionSeeder deliveryOptionSeeder;
    @Autowired private DeliveryOptionRepository deliveryOptionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        // Children first: orders refer to options, payments and items to orders.
        paymentRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        deliveryOptionRepository.deleteAllInBatch();
        deliveryOptionSeeder.seedDefaults();
    }

    // ------------------------------------------------------------------ seed + public list

    @Test
    void theSeedOffersThreeCouriersAndStorePickup() throws Exception {
        // anonymous: the checkout list needs no token
        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].name").value(JT))
                .andExpect(jsonPath("$.data[0].type").value("DELIVERY"))
                .andExpect(jsonPath("$.data[0].fee").value(1.50))
                .andExpect(jsonPath("$.data[0].estimatedTime").value("1–2 days"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data[1].name").value(VET))
                .andExpect(jsonPath("$.data[1].type").value("DELIVERY"))
                .andExpect(jsonPath("$.data[1].fee").value(2.00))
                .andExpect(jsonPath("$.data[1].estimatedTime").value("1–3 days"))
                .andExpect(jsonPath("$.data[2].name").value(RIDER))
                .andExpect(jsonPath("$.data[2].fee").value(1.50))
                .andExpect(jsonPath("$.data[2].estimatedTime").value("Same day"))
                .andExpect(jsonPath("$.data[3].name").value(PICKUP))
                .andExpect(jsonPath("$.data[3].type").value("PICKUP"))
                .andExpect(jsonPath("$.data[3].fee").value(0.00))
                .andExpect(jsonPath("$.data[3].estimatedTime").value("Ready in about 2 hours"));

        // fees always carry two decimals
        assertThat(mockMvc.perform(get(PUBLIC)).andReturn().getResponse().getContentAsString())
                .contains("\"fee\":1.50").contains("\"fee\":2.00").contains("\"fee\":0.00");
    }

    @Test
    void theSeederOnlyFillsAnEmptyTable() throws Exception {
        deliveryOptionSeeder.seedDefaults();
        assertThat(deliveryOptionRepository.count()).isEqualTo(4);

        // once the admin has made choices, a restart does not bring deleted options back
        mockMvc.perform(delete(ADMIN + "/" + idOf(RIDER)).with(asAdmin)).andExpect(status().isOk());
        deliveryOptionSeeder.seedDefaults();
        assertThat(deliveryOptionRepository.count()).isEqualTo(3);
    }

    @Test
    void publicListShowsOnlyEnabledOptionsInDisplayOrder() throws Exception {
        // the seed holds sort orders 0..3
        create(option("Later", "DELIVERY", "3.00", 5, null));
        create(option("Tie A", "DELIVERY", "1.00", 4, null));
        create(option("Tie B", "DELIVERY", "1.00", 4, null));   // same sort order -> id decides
        create(option("Hidden", "DELIVERY", "1.00", 1, false));

        assertThat(names(mockMvc.perform(get(PUBLIC))))
                .containsExactly(JT, VET, RIDER, PICKUP, "Tie A", "Tie B", "Later");

        // the admin sees the disabled one too, with the same ordering rules
        assertThat(names(mockMvc.perform(get(ADMIN).with(asAdmin))))
                .containsExactly(JT, VET, "Hidden", RIDER, PICKUP, "Tie A", "Tie B", "Later");
    }

    // ------------------------------------------------------------------ authorization

    @Test
    void adminEndpointsNeedTheAdminRole() throws Exception {
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json(option("Anon", "DELIVERY", "1.00", null, null))))
                .andExpect(status().isUnauthorized());

        RequestPostProcessor customer = user("shopper").roles("CUSTOMER");
        mockMvc.perform(get(ADMIN).with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN).with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content(json(option("Customer", "DELIVERY", "1.00", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(patch(ADMIN + "/" + idOf(JT) + "/status").with(customer).param("enabled", "false"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN + "/" + idOf(JT)).with(customer)).andExpect(status().isForbidden());

        assertThat(deliveryOptionRepository.count()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ create / update

    @Test
    void createTrimsBlanksAppendsToTheEndAndDefaultsToEnabled() throws Exception {
        Map<String, Object> body = option("  ABA Express  ", "DELIVERY", "3", null, null);
        body.put("description", "   ");
        body.put("estimatedTime", " 2 days ");
        body.put("logoUrl", " /uploads/logos/aba.png ");

        String response = mockMvc.perform(adminJson(post(ADMIN), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery option created"))
                .andExpect(jsonPath("$.data.name").value("ABA Express"))
                .andExpect(jsonPath("$.data.description").value(nullValue()))
                .andExpect(jsonPath("$.data.type").value("DELIVERY"))
                .andExpect(jsonPath("$.data.fee").value(3.00))
                .andExpect(jsonPath("$.data.estimatedTime").value("2 days"))
                .andExpect(jsonPath("$.data.logoUrl").value("/uploads/logos/aba.png"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(4))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("\"fee\":3.00");

        // an explicit position and status are taken as given; an absolute logo URL is fine
        Map<String, Object> pickup = option("Branch pickup", "PICKUP", "0.25", 0, false);
        pickup.put("logoUrl", "https://cdn.example.com/branch.png");
        mockMvc.perform(adminJson(post(ADMIN), pickup))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("PICKUP"))
                .andExpect(jsonPath("$.data.fee").value(0.25))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(names(mockMvc.perform(get(PUBLIC)))).contains("ABA Express").doesNotContain("Branch pickup");
    }

    @Test
    void validationRejectsBadOptions() throws Exception {
        assertFieldError(without(valid(), "name"), "name", "Name is required");
        assertFieldError(with(valid(), "name", "   "), "name", "Name is required");
        assertFieldError(with(valid(), "name", "x".repeat(81)), "name", "Name must be at most 80 characters");
        assertFieldError(with(valid(), "description", "x".repeat(201)), "description",
                "Description must be at most 200 characters");
        assertFieldError(without(valid(), "type"), "type", "Type is required");
        assertFieldError(without(valid(), "fee"), "fee", "Fee is required");
        assertFieldError(with(valid(), "fee", -1), "fee", "Fee cannot be negative");
        assertFieldError(with(valid(), "fee", 1.234), "fee", "Fee must have at most 2 decimal places");
        assertFieldError(with(valid(), "estimatedTime", "x".repeat(61)), "estimatedTime",
                "Estimated time must be at most 60 characters");
        assertFieldError(with(valid(), "logoUrl", "javascript:alert(1)"), "logoUrl", LOGO_ERROR);
        assertFieldError(with(valid(), "logoUrl", "//evil.example/logo.png"), "logoUrl", LOGO_ERROR);
        assertFieldError(with(valid(), "logoUrl", "/" + "x".repeat(500)), "logoUrl",
                "Logo URL must be at most 500 characters");
        assertFieldError(with(valid(), "sortOrder", -1), "sortOrder", "Sort order cannot be negative");

        // not one of DELIVERY / PICKUP
        mockMvc.perform(adminJson(post(ADMIN), with(valid(), "type", "TELEPORT")))
                .andExpect(status().isBadRequest());

        // the same rules guard updates
        mockMvc.perform(adminJson(put(ADMIN + "/" + idOf(JT)), with(valid(), "fee", -0.5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fee").value("Fee cannot be negative"));

        assertThat(deliveryOptionRepository.count()).isEqualTo(4);
        assertThat(deliveryOptionRepository.findById(idOf(JT)).orElseThrow().getFee())
                .isEqualByComparingTo("1.50");
    }

    @Test
    void updateReplacesTheFieldsAndNullFlagsKeepTheirValue() throws Exception {
        long id = create(option("Draft", "DELIVERY", "1.00", 9, false));

        Map<String, Object> body = option(" Branch pickup ", "PICKUP", "0.5", null, null);
        body.put("description", "Toul Kork branch");
        body.put("logoUrl", "https://cdn.example.com/tk.png");
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery option updated"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("Branch pickup"))
                .andExpect(jsonPath("$.data.type").value("PICKUP"))
                .andExpect(jsonPath("$.data.fee").value(0.50))
                .andExpect(jsonPath("$.data.description").value("Toul Kork branch"))
                .andExpect(jsonPath("$.data.estimatedTime").value(nullValue()))
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/tk.png"))
                // null enabled / sortOrder leave them alone
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(9));

        mockMvc.perform(get(ADMIN + "/" + id).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Branch pickup"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        // explicit values do change them
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), option("Branch pickup", "PICKUP", "0.50", 2, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(2));
    }

    @Test
    void unknownOptionIsNotFound() throws Exception {
        mockMvc.perform(get(ADMIN + "/999999").with(asAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Delivery option not found with id 999999"));
        mockMvc.perform(adminJson(put(ADMIN + "/999999"), valid())).andExpect(status().isNotFound());
        mockMvc.perform(patch(ADMIN + "/999999/status").with(asAdmin).param("enabled", "true"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(ADMIN + "/999999/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN + "/999999").with(asAdmin)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ status

    @Test
    void togglingStatusHidesAndRestoresTheOption() throws Exception {
        long vet = idOf(VET);

        mockMvc.perform(patch(ADMIN + "/" + vet + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery option disabled"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(JT, RIDER, PICKUP);
        assertThat(names(mockMvc.perform(get(ADMIN).with(asAdmin)))).contains(VET);

        mockMvc.perform(patch(ADMIN + "/" + vet + "/status").with(asAdmin).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery option enabled"))
                .andExpect(jsonPath("$.data.enabled").value(true));
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(JT, VET, RIDER, PICKUP);

        mockMvc.perform(patch(ADMIN + "/" + vet + "/status").with(asAdmin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theLastEnabledOptionCannotBeDisabledOrDeleted() throws Exception {
        for (String name : List.of(JT, VET, RIDER)) {
            mockMvc.perform(patch(ADMIN + "/" + idOf(name) + "/status").with(asAdmin).param("enabled", "false"))
                    .andExpect(status().isOk());
        }
        long pickup = idOf(PICKUP);

        mockMvc.perform(patch(ADMIN + "/" + pickup + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(LAST_ENABLED));
        mockMvc.perform(adminJson(put(ADMIN + "/" + pickup), option(PICKUP, "PICKUP", "0.00", null, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(LAST_ENABLED));
        mockMvc.perform(delete(ADMIN + "/" + pickup).with(asAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(LAST_ENABLED));

        // nothing changed: the checkout still has its one option
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(PICKUP);

        // editing it without switching it off is fine, and disabled options can still go
        mockMvc.perform(adminJson(put(ADMIN + "/" + pickup), option("Pick up in store", "PICKUP", "0.00", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        mockMvc.perform(delete(ADMIN + "/" + idOf(RIDER)).with(asAdmin)).andExpect(status().isOk());

        // once another option is back on, the old last one may be switched off
        mockMvc.perform(patch(ADMIN + "/" + idOf(JT) + "/status").with(asAdmin).param("enabled", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(ADMIN + "/" + pickup + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isOk());
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(JT);
    }

    // ------------------------------------------------------------------ reorder

    @Test
    void reorderSetsEachSortOrderToItsPosition() throws Exception {
        List<Long> reversed = new ArrayList<>(adminIds());
        Collections.reverse(reversed);

        JsonNode data = data(mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of("ids", reversed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery option order updated")));
        for (int index = 0; index < reversed.size(); index++) {
            assertThat(data.get(index).get("id").asLong()).isEqualTo(reversed.get(index));
            assertThat(data.get(index).get("sortOrder").asInt()).isEqualTo(index);
        }
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(PICKUP, RIDER, VET, JT);
    }

    @Test
    void reorderRejectsIncompleteDuplicatedOrUnknownIds() throws Exception {
        List<Long> ids = adminIds();
        List<List<Long>> bad = List.of(
                ids.subList(1, ids.size()),                                         // one missing
                List.of(ids.get(0), ids.get(0), ids.get(1), ids.get(2)),             // duplicate
                List.of(ids.get(0), ids.get(1), ids.get(2), 999999L));               // unknown
        for (List<Long> attempt : bad) {
            mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of("ids", attempt)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ORDER_ERROR));
        }
        mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ids").value("Option ids are required"));

        // a rejected reorder changes nothing
        assertThat(names(mockMvc.perform(get(PUBLIC)))).containsExactly(JT, VET, RIDER, PICKUP);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletingAnUnusedOptionRemovesIt() throws Exception {
        long id = create(option("Temporary", "DELIVERY", "1.00", null, null));

        mockMvc.perform(delete(ADMIN + "/" + id).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery option deleted"));
        mockMvc.perform(get(ADMIN + "/" + id).with(asAdmin)).andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN + "/" + id).with(asAdmin)).andExpect(status().isNotFound());
        assertThat(names(mockMvc.perform(get(PUBLIC)))).doesNotContain("Temporary");
    }

    @Test
    void anOptionUsedByOrdersCannotBeDeletedOnlyDisabled() throws Exception {
        RequestPostProcessor shopper = user(new CustomUserDetails(newCustomer()));
        long vet = idOf(VET);
        long orderId = placeOrder(shopper, vet);
        placeOrder(shopper, vet);

        mockMvc.perform(delete(ADMIN + "/" + vet).with(asAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("This option is used by 2 order(s). Disable it instead."));
        mockMvc.perform(get(ADMIN + "/" + vet).with(asAdmin)).andExpect(status().isOk());

        // disabling is the way out, and the orders keep showing what was chosen
        mockMvc.perform(patch(ADMIN + "/" + vet + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(shopper))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryOptionId").value(vet))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(VET));

        // an option no order refers to still deletes normally
        mockMvc.perform(delete(ADMIN + "/" + idOf(RIDER)).with(asAdmin)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> option(String name, String type, String fee,
                                              Integer sortOrder, Boolean enabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("fee", new BigDecimal(fee));
        if (sortOrder != null) {
            body.put("sortOrder", sortOrder);
        }
        if (enabled != null) {
            body.put("enabled", enabled);
        }
        return body;
    }

    private static Map<String, Object> valid() {
        return option("Valid courier", "DELIVERY", "1.00", null, null);
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> without(Map<String, Object> body, String key) {
        body.remove(key);
        return body;
    }

    private void assertFieldError(Map<String, Object> body, String field, String message) throws Exception {
        mockMvc.perform(adminJson(post(ADMIN), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors." + field).value(message));
    }

    private long create(Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(adminJson(post(ADMIN), body)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private long idOf(String name) {
        return deliveryOptionRepository.findAll().stream()
                .filter(option -> option.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }

    private List<Long> adminIds() throws Exception {
        List<Long> ids = new ArrayList<>();
        data(mockMvc.perform(get(ADMIN).with(asAdmin))).forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private List<String> names(ResultActions result) throws Exception {
        List<String> names = new ArrayList<>();
        data(result.andExpect(status().isOk())).forEach(node -> names.add(node.get("name").asString()));
        return names;
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private MockHttpServletRequestBuilder adminJson(MockHttpServletRequestBuilder request, Object body) {
        return request.with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    /** One unit of a fresh product, delivered with {@code optionId} to a fresh address. */
    private long placeOrder(RequestPostProcessor shopper, long optionId) throws Exception {
        int n = SEQ.incrementAndGet();
        long categoryId = data(mockMvc.perform(adminJson(post("/api/v1/admin/categories"),
                        Map.of("name", "Delivery cat " + n)))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = data(mockMvc.perform(adminJson(post("/api/v1/admin/products"), Map.of(
                        "categoryId", categoryId, "name", "Parcel " + n, "sku", "SKU-DLV-" + n,
                        "price", 10.00, "stockQuantity", 5)))
                .andExpect(status().isCreated())).get("id").asLong();
        long addressId = data(mockMvc.perform(post("/api/v1/addresses").with(shopper)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recipientName", "Sok Dara", "phone", "012345678",
                                "province", "Phnom Penh"))))
                .andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(post("/api/v1/cart/items").with(shopper)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", productId, "quantity", 1))))
                .andExpect(status().isOk());
        return data(mockMvc.perform(post("/api/v1/orders/checkout").with(shopper)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressId", addressId, "paymentMethod", "CASH_ON_DELIVERY",
                                "deliveryOptionId", optionId))))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"deliveryOptionId\":" + optionId))))
                .get("id").asLong();
    }

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Delivery Shopper " + n)
                .email("delivery-shopper" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }
}
