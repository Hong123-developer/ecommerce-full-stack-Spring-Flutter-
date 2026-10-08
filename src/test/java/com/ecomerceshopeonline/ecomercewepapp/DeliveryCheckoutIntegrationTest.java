package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.config.DeliveryOptionSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.StoreSettingsRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout with "how do you want to receive your order?": the chosen delivery option's fee
 * and snapshot, contact details (given or defaulted from the address), the address's map pin,
 * store pickup without an address, the fallback for clients that name no option, refusal of
 * disabled options - plus map pins on saved addresses and orders from before all this.
 * <p>
 * The class has a database of its own, so every test starts from exactly the four seeded
 * options and no orders: {@link #setUp()} clears both and runs the seeder again.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery_checkout_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class DeliveryCheckoutIntegrationTest {

    private static final String CHECKOUT = "/api/v1/orders/checkout";
    private static final String ADMIN_OPTIONS = "/api/v1/admin/delivery-options";

    private static final String JT = "J&T Express";
    private static final String VET = "Virak Buntham Express (VET)";
    private static final String RIDER = "Store rider (Phnom Penh)";
    private static final String PICKUP = "Pick up at store";

    private static final double LAT = 11.5564;
    private static final double LNG = 104.9282;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DeliveryOptionSeeder deliveryOptionSeeder;
    @Autowired private DeliveryOptionRepository deliveryOptionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreSettingsRepository storeSettingsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");
    private User customer;
    private RequestPostProcessor asCustomer;
    /** 25.00 each, 10 in stock; two of them are in the cart unless a test says otherwise. */
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        // Children first: orders refer to options, payments and items to orders.
        paymentRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        deliveryOptionRepository.deleteAllInBatch();
        deliveryOptionSeeder.seedDefaults();
        storeSettingsRepository.deleteAll();

        customer = newCustomer();
        asCustomer = user(new CustomUserDetails(customer));
        productId = createProduct();
        addToCart(productId, 2);
    }

    // ------------------------------------------------------------------ delivery

    @Test
    void deliveryChargesTheChosenOptionsFeeAndSnapshotsTheOrder() throws Exception {
        long addressId = createAddress(LAT, LNG);
        long vet = idOf(VET);

        Map<String, Object> body = checkout(addressId, vet);
        body.put("note", "  Call before arriving  ");
        JsonNode order = data(mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(50.00))
                .andExpect(jsonPath("$.data.deliveryFee").value(2.00))
                .andExpect(jsonPath("$.data.totalAmount").value(52.00))
                .andExpect(jsonPath("$.data.deliveryOptionId").value(vet))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(VET))
                .andExpect(jsonPath("$.data.deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.shippingAddress").value("No. 12, St. 271, Chamkarmon, Phnom Penh"))
                // blank contact fields default to the address's recipient
                .andExpect(jsonPath("$.data.contactName").value("Sok Dara"))
                .andExpect(jsonPath("$.data.contactPhone").value("012345678"))
                .andExpect(jsonPath("$.data.note").value("Call before arriving"))
                .andExpect(jsonPath("$.data.latitude").value(LAT))
                .andExpect(jsonPath("$.data.longitude").value(LNG)));
        long orderId = order.get("id").asLong();

        // the shopper pays the fee too
        assertThat(paymentRepository.findAll()).singleElement()
                .satisfies(payment -> assertThat(payment.getAmount()).isEqualByComparingTo("52.00"));
        assertThat(stockOf(productId)).isEqualTo(8);

        // later edits to the option and the address do not rewrite the order
        Map<String, Object> repriced = new LinkedHashMap<>(Map.of(
                "name", "VET Express", "type", "DELIVERY", "fee", new BigDecimal("3.00")));
        mockMvc.perform(put(ADMIN_OPTIONS + "/" + vet).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(repriced)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/addresses/" + addressId).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("Moved Away", 13.3633, 103.8564))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryMethodName").value(VET))
                .andExpect(jsonPath("$.data.deliveryFee").value(2.00))
                .andExpect(jsonPath("$.data.totalAmount").value(52.00))
                .andExpect(jsonPath("$.data.contactName").value("Sok Dara"))
                .andExpect(jsonPath("$.data.latitude").value(LAT))
                .andExpect(jsonPath("$.data.longitude").value(LNG))
                .andExpect(jsonPath("$.data.customer").value(nullValue()));

        // the admin sees the same, plus who placed it
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryOptionId").value(vet))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(VET))
                .andExpect(jsonPath("$.data.deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.contactPhone").value("012345678"))
                .andExpect(jsonPath("$.data.note").value("Call before arriving"))
                .andExpect(jsonPath("$.data.latitude").value(LAT))
                .andExpect(jsonPath("$.data.customer.email").value(customer.getEmail()));

        // order lists carry the method too
        mockMvc.perform(get("/api/v1/orders").with(asCustomer))
                .andExpect(jsonPath("$.data.content[0].deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.content[0].deliveryMethodName").value(VET));
        mockMvc.perform(get("/api/v1/admin/orders").with(asAdmin))
                .andExpect(jsonPath("$.data.content[0].deliveryType").value("DELIVERY"));
    }

    @Test
    void contactDetailsGivenAtCheckoutOverrideTheAddress() throws Exception {
        long addressId = createAddress(null, null);

        Map<String, Object> body = checkout(addressId, idOf(RIDER));
        body.put("contactName", "  Chan Vuthy ");
        body.put("contactPhone", " +855 97 765 4321 ");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryMethodName").value(RIDER))
                .andExpect(jsonPath("$.data.deliveryFee").value(1.50))
                .andExpect(jsonPath("$.data.totalAmount").value(51.50))
                .andExpect(jsonPath("$.data.contactName").value("Chan Vuthy"))
                .andExpect(jsonPath("$.data.contactPhone").value("+855 97 765 4321"))
                .andExpect(jsonPath("$.data.note").value(nullValue()))
                // an address without a pin gives an order without one
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()));
    }

    @Test
    void deliveryNeedsOneOfTheShoppersOwnAddresses() throws Exception {
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkout(null, idOf(JT)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a delivery address"))
                .andExpect(jsonPath("$.fieldErrors.addressId").value("Choose a delivery address"));

        long someoneElses = createAddressFor(user(new CustomUserDetails(newCustomer())), null, null);
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkout(someoneElses, idOf(JT)))))
                .andExpect(status().isNotFound());

        assertNothingWasOrdered();
    }

    // ------------------------------------------------------------------ pickup

    @Test
    void pickupNeedsNoAddressAndChargesNoDeliveryFee() throws Exception {
        long pickup = idOf(PICKUP);

        Map<String, Object> body = checkout(null, pickup);
        body.put("paymentMethod", "KHQR");
        body.put("contactName", "Sok Dara");
        body.put("contactPhone", "012 345 678");
        long orderId = data(mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryOptionId").value(pickup))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(PICKUP))
                .andExpect(jsonPath("$.data.deliveryType").value("PICKUP"))
                .andExpect(jsonPath("$.data.subtotal").value(50.00))
                .andExpect(jsonPath("$.data.deliveryFee").value(0.00))
                .andExpect(jsonPath("$.data.totalAmount").value(50.00))
                // the shop has no address on file
                .andExpect(jsonPath("$.data.shippingAddress").value("Pick up at store"))
                .andExpect(jsonPath("$.data.contactName").value("Sok Dara"))
                .andExpect(jsonPath("$.data.contactPhone").value("012 345 678"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.paymentMethod").value("KHQR")))
                .get("id").asLong();

        assertThat(stockOf(productId)).isEqualTo(8);
        mockMvc.perform(get("/api/v1/cart").with(asCustomer))
                .andExpect(jsonPath("$.data.itemCount").value(0));
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId).with(asAdmin))
                .andExpect(jsonPath("$.data.deliveryType").value("PICKUP"))
                .andExpect(jsonPath("$.data.shippingAddress").value("Pick up at store"));
    }

    @Test
    void pickupShowsTheStoreAddressAndIgnoresAnyAddressId() throws Exception {
        storeSettingsRepository.save(StoreSettings.builder()
                .id(StoreSettings.SINGLETON_ID)
                .storeName("Test Shop")
                .address("  No. 1, St. 2, Phnom Penh ")
                .build());
        long addressId = createAddress(LAT, LNG);

        Map<String, Object> body = checkout(addressId, idOf(PICKUP));
        body.put("contactName", "Sok Dara");
        body.put("contactPhone", "012345678");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippingAddress").value("Pick up at store - No. 1, St. 2, Phnom Penh"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.deliveryFee").value(0.00));

        // not even an unknown address id gets in the way of a pickup
        addToCart(productId, 1);
        Map<String, Object> unknownAddress = checkout(999999L, idOf(PICKUP));
        unknownAddress.put("contactName", "Sok Dara");
        unknownAddress.put("contactPhone", "012345678");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(unknownAddress)))
                .andExpect(status().isCreated());
    }

    @Test
    void pickupRequiresAContactNameAndPhone() throws Exception {
        long pickup = idOf(PICKUP);
        long addressId = createAddress(null, null);

        // nothing given - and an address does not stand in for the contact
        for (Map<String, Object> body : List.of(checkout(null, pickup), checkout(addressId, pickup))) {
            mockMvc.perform(post(CHECKOUT).with(asCustomer)
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("One or more fields are invalid"))
                    .andExpect(jsonPath("$.fieldErrors.contactName").value("Customer name is required for store pickup"))
                    .andExpect(jsonPath("$.fieldErrors.contactPhone").value("Phone number is required for store pickup"));
        }

        // blank is the same as missing
        Map<String, Object> blanks = checkout(null, pickup);
        blanks.put("contactName", "   ");
        blanks.put("contactPhone", "  ");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(blanks)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactName").exists())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists());

        // one missing: that field alone, and its message on top
        Map<String, Object> nameOnly = checkout(null, pickup);
        nameOnly.put("contactName", "Sok Dara");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(nameOnly)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone number is required for store pickup"))
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.contactName").doesNotExist());

        assertNothingWasOrdered();
    }

    // ------------------------------------------------------------------ choosing the option

    @Test
    void aCheckoutThatNamesNoOptionGetsTheFirstEnabledDeliveryOption() throws Exception {
        long addressId = createAddress(null, null);
        long vet = idOf(VET);
        // pickup on top, then VET: the first DELIVERY option is VET, not the pickup
        mockMvc.perform(put(ADMIN_OPTIONS + "/order").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(idOf(PICKUP), vet, idOf(JT), idOf(RIDER))))))
                .andExpect(status().isOk());

        // exactly what clients from before delivery options send
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"CASH_ON_DELIVERY\"}".formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryOptionId").value(vet))
                .andExpect(jsonPath("$.data.deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryFee").value(2.00))
                .andExpect(jsonPath("$.data.totalAmount").value(52.00))
                .andExpect(jsonPath("$.data.contactName").value("Sok Dara"));

        // with VET switched off, the next delivery option in line takes over
        mockMvc.perform(patch(ADMIN_OPTIONS + "/" + vet + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isOk());
        addToCart(productId, 1);
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(checkout(addressId, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryMethodName").value(JT))
                .andExpect(jsonPath("$.data.deliveryFee").value(1.50))
                .andExpect(jsonPath("$.data.totalAmount").value(26.50));
    }

    @Test
    void disabledOrUnknownOptionsAreRefused() throws Exception {
        long addressId = createAddress(null, null);
        long vet = idOf(VET);
        mockMvc.perform(patch(ADMIN_OPTIONS + "/" + vet + "/status").with(asAdmin).param("enabled", "false"))
                .andExpect(status().isOk());

        for (long optionId : List.of(vet, 999999L)) {
            mockMvc.perform(post(CHECKOUT).with(asCustomer)
                            .contentType(MediaType.APPLICATION_JSON).content(json(checkout(addressId, optionId))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.deliveryOptionId")
                            .value("This delivery option is not available. Please choose another one."));
        }
        assertNothingWasOrdered();
    }

    @Test
    void aShopThatOnlyOffersPickupMakesTheShopperChoose() throws Exception {
        long addressId = createAddress(null, null);
        for (String name : List.of(JT, VET, RIDER)) {
            mockMvc.perform(patch(ADMIN_OPTIONS + "/" + idOf(name) + "/status").with(asAdmin).param("enabled", "false"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(checkout(addressId, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryOptionId").value("Choose how you want to receive your order"));
        assertNothingWasOrdered();
    }

    @Test
    void withNoOptionConfiguredAtAllTheOldFlatFeeApplies() throws Exception {
        deliveryOptionRepository.deleteAllInBatch();
        long addressId = createAddress(null, null);

        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(checkout(addressId, null))))
                .andExpect(status().isCreated())
                // app.order.delivery-fee
                .andExpect(jsonPath("$.data.deliveryFee").value(1.50))
                .andExpect(jsonPath("$.data.totalAmount").value(51.50))
                .andExpect(jsonPath("$.data.deliveryOptionId").value(nullValue()))
                .andExpect(jsonPath("$.data.deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryMethodName").value("Standard delivery"));
    }

    @Test
    void checkoutFieldsAreValidated() throws Exception {
        long addressId = createAddress(null, null);
        long jt = idOf(JT);

        Map<String, Object> badPhone = checkout(addressId, jt);
        badPhone.put("contactPhone", "call me");
        expectFieldError(badPhone, "contactPhone", "Phone must be 8-30 digits");

        Map<String, Object> longName = checkout(addressId, jt);
        longName.put("contactName", "x".repeat(151));
        expectFieldError(longName, "contactName", "Customer name must be at most 150 characters");

        Map<String, Object> longNote = checkout(addressId, jt);
        longNote.put("note", "x".repeat(501));
        expectFieldError(longNote, "note", "Note must be at most 500 characters");

        Map<String, Object> noPayment = checkout(addressId, jt);
        noPayment.remove("paymentMethod");
        expectFieldError(noPayment, "paymentMethod", "Payment method is required");

        // the limits apply to the trimmed text
        Map<String, Object> paddedNote = checkout(addressId, jt);
        paddedNote.put("note", "  " + "x".repeat(500) + "  ");
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(paddedNote)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.note").value("x".repeat(500)));
    }

    // ------------------------------------------------------------------ older orders

    @Test
    void ordersFromBeforeDeliveryOptionsStillRender() throws Exception {
        Order legacy = orderRepository.save(Order.builder()
                .user(customer)
                .orderNumber("ORD-LEGACY-" + SEQ.incrementAndGet())
                .status(OrderStatus.PENDING)
                .shippingAddress("No. 12, St. 271, Phnom Penh")
                .subtotal(new BigDecimal("10.00"))
                .deliveryFee(new BigDecimal("1.50"))
                .discount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("11.50"))
                .build());

        for (String path : List.of("/api/v1/orders/", "/api/v1/admin/orders/")) {
            RequestPostProcessor who = path.contains("admin") ? asAdmin : asCustomer;
            mockMvc.perform(get(path + legacy.getId()).with(who))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalAmount").value(11.50))
                    .andExpect(jsonPath("$.data.deliveryOptionId").value(nullValue()))
                    .andExpect(jsonPath("$.data.deliveryMethodName").value(nullValue()))
                    .andExpect(jsonPath("$.data.deliveryType").value(nullValue()))
                    .andExpect(jsonPath("$.data.contactName").value(nullValue()))
                    .andExpect(jsonPath("$.data.contactPhone").value(nullValue()))
                    .andExpect(jsonPath("$.data.note").value(nullValue()))
                    .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                    .andExpect(jsonPath("$.data.longitude").value(nullValue()));
        }
        mockMvc.perform(get("/api/v1/orders").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].deliveryType").value(nullValue()));
        mockMvc.perform(get("/api/v1/admin/orders").with(asAdmin)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ address map pins

    @Test
    void addressesKeepAMapPinThatAFullUpdateCanMoveOrClear() throws Exception {
        String created = mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(address("Sok Dara", LAT, LNG))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.latitude").value(LAT))
                .andExpect(jsonPath("$.data.longitude").value(LNG))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/addresses").with(asCustomer))
                .andExpect(jsonPath("$.data[0].latitude").value(LAT));

        mockMvc.perform(put("/api/v1/addresses/" + id).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(address("Sok Dara", -33.8688, 151.2093))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(-33.8688))
                .andExpect(jsonPath("$.data.longitude").value(151.2093));

        // full replacement: no coordinates in the body removes the pin
        mockMvc.perform(put("/api/v1/addresses/" + id).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(address("Sok Dara", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()));
    }

    @Test
    void addressCoordinatesAreValidated() throws Exception {
        expectAddressError(address("A", 90.5, 10.0), "latitude", "Latitude must be between -90 and 90");
        expectAddressError(address("A", -91.0, 10.0), "latitude", "Latitude must be between -90 and 90");
        expectAddressError(address("A", 10.0, 180.5), "longitude", "Longitude must be between -180 and 180");
        expectAddressError(address("A", 10.0, -181.0), "longitude", "Longitude must be between -180 and 180");
        expectAddressError(address("A", 10.0, null), "longitude", "Latitude and longitude must be given together");
        expectAddressError(address("A", null, 10.0), "latitude", "Latitude and longitude must be given together");

        // the extremes themselves are fine
        mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(address("Edge", -90.0, 180.0))))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> checkout(Long addressId, Long optionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (addressId != null) {
            body.put("addressId", addressId);
        }
        body.put("paymentMethod", "CASH_ON_DELIVERY");
        if (optionId != null) {
            body.put("deliveryOptionId", optionId);
        }
        return body;
    }

    private static Map<String, Object> address(String recipient, Double latitude, Double longitude) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientName", recipient);
        body.put("phone", "012345678");
        body.put("province", "Phnom Penh");
        body.put("district", "Chamkarmon");
        body.put("addressDetail", "No. 12, St. 271");
        if (latitude != null) {
            body.put("latitude", latitude);
        }
        if (longitude != null) {
            body.put("longitude", longitude);
        }
        return body;
    }

    private long createAddress(Double latitude, Double longitude) throws Exception {
        return createAddressFor(asCustomer, latitude, longitude);
    }

    private long createAddressFor(RequestPostProcessor who, Double latitude, Double longitude) throws Exception {
        return data(mockMvc.perform(post("/api/v1/addresses").with(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("Sok Dara", latitude, longitude))))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private void expectFieldError(Map<String, Object> body, String field, String message) throws Exception {
        mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(message));
    }

    private void expectAddressError(Map<String, Object> body, String field, String message) throws Exception {
        mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(message));
    }

    /** A refused checkout must leave the stock, the cart and the order table untouched. */
    private void assertNothingWasOrdered() throws Exception {
        assertThat(orderRepository.count()).isZero();
        assertThat(stockOf(productId)).isEqualTo(10);
        mockMvc.perform(get("/api/v1/cart").with(asCustomer))
                .andExpect(jsonPath("$.data.totalQuantity").value(2));
    }

    private long idOf(String name) {
        return deliveryOptionRepository.findAll().stream()
                .filter(option -> option.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }

    private long createProduct() throws Exception {
        int n = SEQ.incrementAndGet();
        long categoryId = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Checkout cat " + n))))
                .andExpect(status().isCreated())).get("id").asLong();
        return data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", categoryId, "name", "Headphones " + n,
                                "sku", "SKU-CHK-" + n, "price", new BigDecimal("25.00"), "stockQuantity", 10))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private void addToCart(long product, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", product, "quantity", quantity))))
                .andExpect(status().isOk());
    }

    private int stockOf(long product) {
        return productRepository.findById(product).orElseThrow().getStockQuantity();
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Checkout Shopper " + n)
                .email("checkout-shopper" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }
}
