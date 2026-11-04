package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.config.DeliveryOptionSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.DeliveryOption;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderStatusHistory;
import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.ShipmentStage;
import com.ecomerceshopeonline.ecomercewepapp.repository.DeliveryOptionRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.NotificationRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderStatusHistoryRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.PaymentRepository;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The courier's side of a shipped order: the admin enters the tracking number, carrier,
 * courier, estimated arrival and courier step, and reports the courier's position; the
 * customer's tracking endpoint shows all of it together with the destination, the store's
 * support contacts and a timeline that gains a step (and a notification) per new stage.
 * <p>
 * The class has a database of its own; {@link #setUp()} clears every order (children
 * first: history, notifications, payments and items before the orders they refer to) and
 * the store settings, and reseeds the default delivery options.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shipment_tracking_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class ShipmentTrackingIntegrationTest {

    private static final String CHECKOUT = "/api/v1/orders/checkout";
    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final String JT = "J&T Express";
    private static final String PICKUP = "Pick up at store";

    private static final double HOME_LAT = 11.5449;
    private static final double HOME_LNG = 104.9214;
    private static final double COURIER_LAT = 11.5612;
    private static final double COURIER_LNG = 104.9165;

    private static final String NOT_SHIPPED = "Shipment stage can only be set while the order is shipped";
    private static final String PICKUP_HAS_NO_STAGES = "Pickup orders have no courier stages";

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DeliveryOptionSeeder deliveryOptionSeeder;
    @Autowired private DeliveryOptionRepository deliveryOptionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private StoreSettingsRepository storeSettingsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asCustomer;
    /** 25.00 each, 10 in stock. */
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        // Children first: history rows and notifications refer to orders.
        orderStatusHistoryRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        deliveryOptionRepository.deleteAllInBatch();
        deliveryOptionSeeder.seedDefaults();
        storeSettingsRepository.deleteAll();

        User admin = userRepository.findByEmail("admin@test.local").orElseThrow();
        asAdmin = user(new CustomUserDetails(admin));
        asCustomer = user(new CustomUserDetails(newCustomer()));
        productId = createProduct();
    }

    // ------------------------------------------------------------------ the full picture

    @Test
    void theCustomerSeesEverythingTheAdminEntered() throws Exception {
        storeSettingsRepository.save(StoreSettings.builder()
                .id(StoreSettings.SINGLETON_ID)
                .storeName("Test Shop")
                .supportPhone("+855 23 000 000")
                .supportEmail("help@shop.com")
                .build());
        JsonNode placed = placeDeliveryOrder();
        long orderId = placed.get("id").asLong();
        String orderNumber = placed.get("orderNumber").asText();
        setStatus(orderId, "SHIPPED");

        // the admin's answer is the customer's tracking view
        shipment(orderId, fullShipment("OUT_FOR_DELIVERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.shipmentStage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.trackingNumber").value("JT8800123456"))
                .andExpect(jsonPath("$.data.courier.name").value("Sokha"))
                .andExpect(jsonPath("$.data.courier.latitude").value(nullValue()));
        location(orderId, COURIER_LAT, COURIER_LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courier.latitude").value(COURIER_LAT));

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/tracking").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.currentStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.data.shipmentStage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(JT))
                .andExpect(jsonPath("$.data.estimatedDeliveryAt").value("2026-11-03T16:30:00"))
                // the delivery option's own estimate, read through the lazy relation
                .andExpect(jsonPath("$.data.estimatedTime").value(option(JT).getEstimatedTime()))
                .andExpect(jsonPath("$.data.trackingNumber").value("JT8800123456"))
                .andExpect(jsonPath("$.data.carrierName").value("J&T Express"))
                .andExpect(jsonPath("$.data.courier.name").value("Sokha"))
                .andExpect(jsonPath("$.data.courier.phone").value("+855 12 345 678"))
                .andExpect(jsonPath("$.data.courier.latitude").value(COURIER_LAT))
                .andExpect(jsonPath("$.data.courier.longitude").value(COURIER_LNG))
                .andExpect(jsonPath("$.data.courier.locationUpdatedAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")))
                .andExpect(jsonPath("$.data.destination.address").value("St 271, Toul Tom Poung, Phnom Penh"))
                .andExpect(jsonPath("$.data.destination.latitude").value(HOME_LAT))
                .andExpect(jsonPath("$.data.destination.longitude").value(HOME_LNG))
                .andExpect(jsonPath("$.data.destination.contactName").value("Dara"))
                .andExpect(jsonPath("$.data.destination.contactPhone").value("012 345 678"))
                .andExpect(jsonPath("$.data.support.phone").value("+855 23 000 000"))
                .andExpect(jsonPath("$.data.support.email").value("help@shop.com"))
                // placed, shipped, out for delivery - oldest first
                .andExpect(jsonPath("$.data.events.length()").value(3))
                .andExpect(jsonPath("$.data.events[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.events[0].stage").value(nullValue()))
                .andExpect(jsonPath("$.data.events[1].status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.events[1].stage").value(nullValue()))
                .andExpect(jsonPath("$.data.events[2].status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.events[2].stage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.events[2].note").value(nullValue()))
                .andExpect(jsonPath("$.data.events[2].createdAt").exists());

        // the order pages carry the shipment values too
        for (String path : List.of("/api/v1/admin/orders/", "/api/v1/orders/")) {
            RequestPostProcessor who = path.contains("admin") ? asAdmin : asCustomer;
            mockMvc.perform(get(path + orderId).with(who))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.trackingNumber").value("JT8800123456"))
                    .andExpect(jsonPath("$.data.carrierName").value("J&T Express"))
                    .andExpect(jsonPath("$.data.courierName").value("Sokha"))
                    .andExpect(jsonPath("$.data.courierPhone").value("+855 12 345 678"))
                    .andExpect(jsonPath("$.data.estimatedDeliveryAt").value("2026-11-03T16:30:00"))
                    .andExpect(jsonPath("$.data.shipmentStage").value("OUT_FOR_DELIVERY"));
        }
    }

    @Test
    void courierIsNullWhileNothingAboutTheCourierIsKnown() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");

        // nothing entered yet, and no store settings saved
        tracking(orderId)
                .andExpect(jsonPath("$.data.courier").value(nullValue()))
                .andExpect(jsonPath("$.data.support").value(nullValue()))
                .andExpect(jsonPath("$.data.shipmentStage").value(nullValue()))
                .andExpect(jsonPath("$.data.trackingNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.estimatedDeliveryAt").value(nullValue()))
                .andExpect(jsonPath("$.data.destination.address").value("St 271, Toul Tom Poung, Phnom Penh"));

        // a parcel reference alone says nothing about the courier
        Map<String, Object> parcelOnly = new LinkedHashMap<>();
        parcelOnly.put("trackingNumber", "JT1");
        parcelOnly.put("carrierName", "J&T Express");
        shipment(orderId, parcelOnly)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("JT1"))
                .andExpect(jsonPath("$.data.courier").value(nullValue()));

        // a phone alone does
        shipment(orderId, Map.of("courierPhone", "012 999 888"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courier.name").value(nullValue()))
                .andExpect(jsonPath("$.data.courier.phone").value("012 999 888"))
                .andExpect(jsonPath("$.data.courier.locationUpdatedAt").value(nullValue()))
                // full replacement: what the second save left out is gone
                .andExpect(jsonPath("$.data.trackingNumber").value(nullValue()));
    }

    @Test
    void aPickupOrderHasNoPinButStillATimeline() throws Exception {
        long orderId = placePickupOrder();

        tracking(orderId)
                .andExpect(jsonPath("$.data.deliveryType").value("PICKUP"))
                .andExpect(jsonPath("$.data.deliveryMethodName").value(PICKUP))
                .andExpect(jsonPath("$.data.estimatedTime").value(option(PICKUP).getEstimatedTime()))
                .andExpect(jsonPath("$.data.destination.address").value("Pick up at store"))
                .andExpect(jsonPath("$.data.destination.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.destination.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.destination.contactName").value("Sok Dara"))
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].note").value("Order placed"));
    }

    // ------------------------------------------------------------------ stages

    @Test
    void aNewStageAddsATimelineStepAndTellsTheCustomer() throws Exception {
        JsonNode placed = placeDeliveryOrder();
        long orderId = placed.get("id").asLong();
        String number = placed.get("orderNumber").asText();
        setStatus(orderId, "SHIPPED");
        long unreadAfterShipping = unread(asCustomer);

        shipment(orderId, fullShipment("PICKED_UP")).andExpect(status().isOk());

        assertThat(stagesOf(orderId)).containsExactly(ShipmentStage.PICKED_UP);
        JsonNode latest = latestNotification();
        assertThat(latest.get("type").asText()).isEqualTo("ORDER_UPDATED");
        assertThat(latest.get("title").asText()).isEqualTo("Picked up by courier");
        assertThat(latest.get("message").asText())
                .isEqualTo("Order #%s has been picked up by the courier.".formatted(number));
        assertThat(latest.get("link").asText()).isEqualTo("/orders/" + orderId);
        assertThat(latest.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(latest.get("orderStatus").asText()).isEqualTo("SHIPPED");
        assertThat(unread(asCustomer)).isEqualTo(unreadAfterShipping + 1);

        shipment(orderId, fullShipment("IN_TRANSIT")).andExpect(status().isOk());
        assertThat(latestNotification().get("title").asText()).isEqualTo("Order in transit");
        assertThat(latestNotification().get("message").asText())
                .isEqualTo("Order #%s is in transit.".formatted(number));

        shipment(orderId, fullShipment("OUT_FOR_DELIVERY")).andExpect(status().isOk());
        assertThat(latestNotification().get("title").asText()).isEqualTo("Out for delivery");
        assertThat(latestNotification().get("message").asText())
                .isEqualTo("Order #%s is out for delivery and will arrive soon.".formatted(number));

        assertThat(stagesOf(orderId)).containsExactly(
                ShipmentStage.PICKED_UP, ShipmentStage.IN_TRANSIT, ShipmentStage.OUT_FOR_DELIVERY);
        assertThat(unread(asCustomer)).isEqualTo(unreadAfterShipping + 3);
        // the admin moved it on - nothing new in their own bell
        assertThat(list(asAdmin).get("content")).hasSize(1);

        tracking(orderId)
                .andExpect(jsonPath("$.data.events.length()").value(5))
                .andExpect(jsonPath("$.data.events[2].stage").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.events[3].stage").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.events[4].stage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.events[4].status").value("SHIPPED"));
    }

    @Test
    void savingTheSameStageAgainIsNotANewStep() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");
        shipment(orderId, fullShipment("IN_TRANSIT")).andExpect(status().isOk());
        long historyRows = orderStatusHistoryRepository.count();
        long notifications = notificationRepository.count();

        // the form re-sends the stage with every other edit
        Map<String, Object> edited = fullShipment("IN_TRANSIT");
        edited.put("trackingNumber", "JT0000000001");
        shipment(orderId, edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("JT0000000001"))
                .andExpect(jsonPath("$.data.shipmentStage").value("IN_TRANSIT"));

        assertThat(orderStatusHistoryRepository.count()).isEqualTo(historyRows);
        assertThat(notificationRepository.count()).isEqualTo(notifications);
        assertThat(stagesOf(orderId)).containsExactly(ShipmentStage.IN_TRANSIT);

        // taking the stage away is not a step either
        shipment(orderId, fullShipment(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentStage").value(nullValue()));
        assertThat(orderStatusHistoryRepository.count()).isEqualTo(historyRows);
        assertThat(notificationRepository.count()).isEqualTo(notifications);
    }

    @Test
    void aStageNeedsAShippedDeliveryOrder() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();

        for (String current : List.of("PENDING", "PROCESSING")) {
            if (!current.equals("PENDING")) {
                setStatus(orderId, current);
            }
            shipment(orderId, fullShipment("PICKED_UP"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(NOT_SHIPPED));
        }
        // the refused save changed nothing
        assertThat(orderRepository.findById(orderId).orElseThrow().getTrackingNumber()).isNull();

        // the rest of the details can be entered ahead of shipping
        shipment(orderId, fullShipment(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.trackingNumber").value("JT8800123456"));

        long pickupId = placePickupOrder();
        setStatus(pickupId, "SHIPPED");
        shipment(pickupId, fullShipment("PICKED_UP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PICKUP_HAS_NO_STAGES));
        assertThat(orderRepository.findById(pickupId).orElseThrow().getShipmentStage()).isNull();

        assertThat(stagesOf(orderId)).isEmpty();
        assertThat(stagesOf(pickupId)).isEmpty();

        // an unknown stage or order
        mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/shipment").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"LOST\"}"))
                .andExpect(status().isBadRequest());
        shipment(999999L, fullShipment(null)).andExpect(status().isNotFound());
    }

    @Test
    void leavingShippedClearsTheStage() throws Exception {
        long delivered = placeDeliveryOrder().get("id").asLong();
        setStatus(delivered, "SHIPPED");
        shipment(delivered, fullShipment("OUT_FOR_DELIVERY")).andExpect(status().isOk());

        setStatus(delivered, "DELIVERED");

        tracking(delivered)
                .andExpect(jsonPath("$.data.currentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.shipmentStage").value(nullValue()))
                // the rest of the shipment and the steps taken stay on record
                .andExpect(jsonPath("$.data.trackingNumber").value("JT8800123456"))
                .andExpect(jsonPath("$.data.events[2].stage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.events[3].status").value("DELIVERED"));
        mockMvc.perform(get("/api/v1/admin/orders/" + delivered).with(asAdmin))
                .andExpect(jsonPath("$.data.shipmentStage").value(nullValue()));

        // the store cancelling a shipped order clears it too
        long cancelled = placeDeliveryOrder().get("id").asLong();
        setStatus(cancelled, "SHIPPED");
        shipment(cancelled, fullShipment("IN_TRANSIT")).andExpect(status().isOk());
        setStatus(cancelled, "CANCELLED");
        assertThat(orderRepository.findById(cancelled).orElseThrow().getShipmentStage()).isNull();

        // and so does moving a shipped order back a step
        long movedBack = placeDeliveryOrder().get("id").asLong();
        setStatus(movedBack, "SHIPPED");
        shipment(movedBack, fullShipment("PICKED_UP")).andExpect(status().isOk());
        setStatus(movedBack, "PROCESSING");
        assertThat(orderRepository.findById(movedBack).orElseThrow().getShipmentStage()).isNull();
    }

    // ------------------------------------------------------------------ courier location

    @Test
    void theCourierLocationIsRecordedWithItsTime() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();

        location(orderId, COURIER_LAT, COURIER_LNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Courier location can only be set while the order is shipped"));

        setStatus(orderId, "SHIPPED");
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        location(orderId, COURIER_LAT, COURIER_LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courier.name").value(nullValue()))
                .andExpect(jsonPath("$.data.courier.latitude").value(COURIER_LAT))
                .andExpect(jsonPath("$.data.courier.longitude").value(COURIER_LNG))
                .andExpect(jsonPath("$.data.courier.locationUpdatedAt").exists());

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getCourierLatitude()).isEqualTo(COURIER_LAT);
        assertThat(saved.getCourierLongitude()).isEqualTo(COURIER_LNG);
        assertThat(saved.getCourierLocationAt()).isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));

        // the customer sees the same pin
        tracking(orderId)
                .andExpect(jsonPath("$.data.courier.latitude").value(COURIER_LAT))
                .andExpect(jsonPath("$.data.courier.longitude").value(COURIER_LNG));

        // once delivered, the courier has nowhere left to report from
        setStatus(orderId, "DELIVERED");
        location(orderId, 11.0, 104.0).andExpect(status().isBadRequest());
        assertThat(orderRepository.findById(orderId).orElseThrow().getCourierLatitude()).isEqualTo(COURIER_LAT);
    }

    @Test
    void courierCoordinatesAreValidated() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");

        location(orderId, 91.0, COURIER_LNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").value("Latitude must be between -90 and 90"));
        location(orderId, -90.5, COURIER_LNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").value("Latitude must be between -90 and 90"));
        location(orderId, COURIER_LAT, 180.5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.longitude").value("Longitude must be between -180 and 180"));
        mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/shipment/location").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("longitude", COURIER_LNG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").value("Latitude is required"));
        mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/shipment/location").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("latitude", COURIER_LAT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.longitude").value("Longitude is required"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getCourierLocationAt()).isNull();

        // the extremes themselves are fine
        location(orderId, -90.0, 180.0).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ input

    @Test
    void blankTextIsStoredAsNullAndTheRestTrimmed() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trackingNumber", "   ");
        body.put("carrierName", "  J&T Express  ");
        body.put("courierName", "");
        body.put("courierPhone", "  ");
        shipment(orderId, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrierName").value("J&T Express"))
                .andExpect(jsonPath("$.data.trackingNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.courier").value(nullValue()));

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getTrackingNumber()).isNull();
        assertThat(saved.getCarrierName()).isEqualTo("J&T Express");
        assertThat(saved.getCourierName()).isNull();
        assertThat(saved.getCourierPhone()).isNull();
        assertThat(saved.getEstimatedDeliveryAt()).isNull();
        assertThat(saved.getShipmentStage()).isNull();

        // the limits apply to the trimmed text
        Map<String, Object> padded = new LinkedHashMap<>();
        padded.put("trackingNumber", "  " + "X".repeat(60) + "  ");
        padded.put("courierPhone", " +855 (12) 345-678 ");
        shipment(orderId, padded)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("X".repeat(60)))
                .andExpect(jsonPath("$.data.courier.phone").value("+855 (12) 345-678"));
    }

    @Test
    void shipmentFieldsAreValidated() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");

        expectShipmentError(orderId, "courierPhone", "call me", "Courier phone must be 6-30 digits, spaces or + ( ) -");
        expectShipmentError(orderId, "courierPhone", "12345", "Courier phone must be 6-30 digits, spaces or + ( ) -");
        expectShipmentError(orderId, "trackingNumber", "X".repeat(61), "Tracking number must be at most 60 characters");
        expectShipmentError(orderId, "carrierName", "X".repeat(81), "Carrier name must be at most 80 characters");
        expectShipmentError(orderId, "courierName", "X".repeat(121), "Courier name must be at most 120 characters");

        Order unchanged = orderRepository.findById(orderId).orElseThrow();
        assertThat(unchanged.getCourierPhone()).isNull();
        assertThat(unchanged.getTrackingNumber()).isNull();
    }

    // ------------------------------------------------------------------ who may do what

    @Test
    void onlyAdminsMayEnterShipments() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");
        String shipmentUrl = "/api/v1/admin/orders/" + orderId + "/shipment";
        String locationUrl = shipmentUrl + "/location";
        String shipmentBody = json(fullShipment("PICKED_UP"));
        String locationBody = json(Map.of("latitude", COURIER_LAT, "longitude", COURIER_LNG));

        mockMvc.perform(put(shipmentUrl).contentType(MediaType.APPLICATION_JSON).content(shipmentBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(locationUrl).contentType(MediaType.APPLICATION_JSON).content(locationBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(shipmentUrl).with(asCustomer).contentType(MediaType.APPLICATION_JSON).content(shipmentBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(put(locationUrl).with(asCustomer).contentType(MediaType.APPLICATION_JSON).content(locationBody))
                .andExpect(status().isForbidden());

        Order untouched = orderRepository.findById(orderId).orElseThrow();
        assertThat(untouched.getShipmentStage()).isNull();
        assertThat(untouched.getTrackingNumber()).isNull();
        assertThat(untouched.getCourierLatitude()).isNull();
        assertThat(stagesOf(orderId)).isEmpty();
    }

    @Test
    void nobodyElseCanReadTheTracking() throws Exception {
        long orderId = placeDeliveryOrder().get("id").asLong();
        setStatus(orderId, "SHIPPED");
        shipment(orderId, fullShipment("PICKED_UP")).andExpect(status().isOk());

        // someone else's order is "not found", exactly like an order that does not exist
        RequestPostProcessor stranger = user(new CustomUserDetails(newCustomer()));
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/tracking").with(stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orders/999999/tracking").with(stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/tracking"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    /** The admin form's full payload; {@code stage} may be null. */
    private static Map<String, Object> fullShipment(String stage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trackingNumber", "JT8800123456");
        body.put("carrierName", "J&T Express");
        body.put("courierName", "Sokha");
        body.put("courierPhone", "+855 12 345 678");
        body.put("estimatedDeliveryAt", "2026-11-03T16:30:00");
        body.put("stage", stage);
        return body;
    }

    private ResultActions shipment(long orderId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/shipment").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions location(long orderId, double latitude, double longitude) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/shipment/location").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("latitude", latitude, "longitude", longitude))));
    }

    private ResultActions tracking(long orderId) throws Exception {
        return mockMvc.perform(get("/api/v1/orders/" + orderId + "/tracking").with(asCustomer))
                .andExpect(status().isOk());
    }

    private void expectShipmentError(long orderId, String field, String value, String message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, value);
        shipment(orderId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(message));
    }

    /** The courier steps on the order's timeline, oldest first. */
    private List<ShipmentStage> stagesOf(long orderId) {
        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(OrderStatusHistory::getShipmentStage)
                .filter(stage -> stage != null)
                .toList();
    }

    private void setStatus(long orderId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", status))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(status));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.valueOf(status));
    }

    /** One unit, J&T, to a pinned address in Phnom Penh, with "Dara" to call. */
    private JsonNode placeDeliveryOrder() throws Exception {
        addToCart(productId, 1);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("recipientName", "Sok Dara");
        address.put("phone", "012345678");
        address.put("province", "Phnom Penh");
        address.put("district", "Toul Tom Poung");
        address.put("addressDetail", "St 271");
        address.put("latitude", HOME_LAT);
        address.put("longitude", HOME_LNG);
        long addressId = data(mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(address)))
                .andExpect(status().isCreated())).get("id").asLong();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("deliveryOptionId", option(JT).getId());
        body.put("paymentMethod", "CASH_ON_DELIVERY");
        body.put("contactName", "Dara");
        body.put("contactPhone", "012 345 678");
        return data(mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()));
    }

    private long placePickupOrder() throws Exception {
        addToCart(productId, 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deliveryOptionId", option(PICKUP).getId());
        body.put("paymentMethod", "CASH_ON_DELIVERY");
        body.put("contactName", "Sok Dara");
        body.put("contactPhone", "012345678");
        return data(mockMvc.perform(post(CHECKOUT).with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private DeliveryOption option(String name) {
        return deliveryOptionRepository.findAll().stream()
                .filter(option -> option.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private JsonNode latestNotification() throws Exception {
        return list(asCustomer).get("content").get(0);
    }

    private JsonNode list(RequestPostProcessor who) throws Exception {
        return data(mockMvc.perform(get(NOTIFICATIONS).with(who)).andExpect(status().isOk()));
    }

    private long unread(RequestPostProcessor who) throws Exception {
        return data(mockMvc.perform(get(NOTIFICATIONS + "/unread-count").with(who)).andExpect(status().isOk()))
                .get("count").asLong();
    }

    private long createProduct() throws Exception {
        int n = SEQ.incrementAndGet();
        long categoryId = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Tracking cat " + n))))
                .andExpect(status().isCreated())).get("id").asLong();
        return data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", categoryId, "name", "Parcel " + n,
                                "sku", "SKU-TRACK-" + n, "price", new BigDecimal("25.00"), "stockQuantity", 10))))
                .andExpect(status().isCreated())).get("id").asLong();
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

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Tracking Shopper " + n)
                .email("tracking-shopper" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }
}
