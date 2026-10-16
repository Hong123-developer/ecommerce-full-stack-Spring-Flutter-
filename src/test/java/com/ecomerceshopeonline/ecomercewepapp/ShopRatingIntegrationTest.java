package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop's own star score ("Shop rating"): set / clear / left alone by clients that don't send
 * it, half-star validation, the optional label, and that shoppers get it next to - never inside -
 * the customer rating.
 */
@SpringBootTest
class ShopRatingIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String MESSAGE = "Shop rating must be between 0.5 and 5 in steps of 0.5";
    private static final String LABEL = "Rated on our Facebook page";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;
    private final RequestPostProcessor admin = user("admin").roles("ADMIN");
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        String body = mockMvc.perform(post("/api/v1/admin/categories").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shop rating " + SEQ.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        categoryId = objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    /** A product body as a client that knows nothing about shop ratings sends it. */
    private Map<String, Object> product(String sku) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", "Rated phone " + sku);
        body.put("sku", sku);
        body.put("price", 199.00);
        body.put("stockQuantity", 4);
        return body;
    }

    private Map<String, Object> product(String sku, Object shopRating, Object shopRatingLabel) {
        Map<String, Object> body = product(sku);
        body.put("shopRating", shopRating);
        body.put("shopRatingLabel", shopRatingLabel);
        return body;
    }

    private String newSku() {
        return "SKU-SHOPRATE-" + SEQ.incrementAndGet();
    }

    private long create(Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private ResultActions update(long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/products/" + id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void shopRatingIsStoredAndShownToShoppersApartFromCustomerRating() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(newSku(), 4.5, "  " + LABEL + "  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("data").get("id").asLong();

        // public detail: the shop's score sits next to the customer one, which stays empty
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL))
                .andExpect(jsonPath("$.data.rating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));

        // public list
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].shopRating").value(4.5))
                .andExpect(jsonPath("$.data.content[0].shopRatingLabel").value(LABEL))
                .andExpect(jsonPath("$.data.content[0].reviewCount").value(0));

        // admin detail
        mockMvc.perform(get("/api/v1/admin/products/" + id).with(admin))
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));
    }

    @Test
    void wholeStarsAndTheEndsOfTheRangeAreAccepted() throws Exception {
        String sku = newSku();
        long id = create(product(sku, 5, null));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.shopRating").value(5.0));

        update(id, product(sku, 0.5, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(0.5));

        update(id, product(sku, 3, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(3.0));
    }

    @Test
    void productsWithoutAShopRatingReturnNull() throws Exception {
        long plain = create(product(newSku()));
        mockMvc.perform(get("/api/v1/products/" + plain))
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(nullValue()));

        // 0 on create also means "none", and a blank label is no label
        long zero = create(product(newSku(), 0, "   "));
        mockMvc.perform(get("/api/v1/admin/products/" + zero).with(admin))
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(nullValue()));
    }

    @Test
    void updatesLeaveTheShopRatingAloneUnlessTheyNameIt() throws Exception {
        String sku = newSku();
        long id = create(product(sku, 4.5, LABEL));

        // a client that doesn't know about shop ratings (variants editor, older pages...)
        update(id, product(sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));

        // explicit nulls mean the same
        update(id, product(sku, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));

        // variants sent without the shop rating
        Map<String, Object> withVariants = product(sku);
        withVariants.put("optionNames", List.of("Storage"));
        withVariants.put("variants", List.of(Map.of("options", List.of("128GB"),
                "sku", sku + "-128", "price", 199.00, "stockQuantity", 3)));
        update(id, withVariants)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants", hasSize(1)))
                .andExpect(jsonPath("$.data.shopRating").value(4.5));

        // show / hide toggle from the products list
        mockMvc.perform(patch("/api/v1/admin/products/" + id + "/status").with(admin)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(4.5));
        mockMvc.perform(patch("/api/v1/admin/products/" + id + "/status").with(admin)
                        .param("enabled", "true"))
                .andExpect(jsonPath("$.data.shopRating").value(4.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));

        // change the stars only: the label stays
        update(id, product(sku, 3.5, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(3.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));

        // clear the label only: the stars stay
        update(id, product(sku, null, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(3.5))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(nullValue()));

        // 0 removes the shop rating
        update(id, product(sku, 0, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(nullValue()));
    }

    @Test
    void aLabelWithoutStarsIsKeptForTheAdminButNotShownToShoppers() throws Exception {
        long id = create(product(newSku(), null, LABEL));

        mockMvc.perform(get("/api/v1/admin/products/" + id).with(admin))
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(LABEL));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.shopRating").value(nullValue()))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(nullValue()));
    }

    @Test
    void ratingsThatAreNotHalfStarsAreRejected() throws Exception {
        for (Object bad : List.of(0.3, 5.5, 4.25, -1, 0.49, 6, 10)) {
            mockMvc.perform(post("/api/v1/admin/products").with(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(product(newSku(), bad, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.shopRating").value(MESSAGE));
        }

        String sku = newSku();
        long id = create(product(sku, 4.0, null));
        for (Object bad : List.of(0.3, 5.5, 4.25)) {
            update(id, product(sku, bad, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.shopRating").value(MESSAGE));
        }
        mockMvc.perform(get("/api/v1/admin/products/" + id).with(admin))
                .andExpect(jsonPath("$.data.shopRating").value(4.0));
    }

    @Test
    void labelIsLimitedTo60Characters() throws Exception {
        String sixty = "x".repeat(60);
        long id = create(product(newSku(), 4.5, sixty));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.shopRatingLabel").value(sixty));

        mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(newSku(), 4.5, "x".repeat(61)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.shopRatingLabel")
                        .value("Shop rating label must be at most 60 characters"));
    }
}
