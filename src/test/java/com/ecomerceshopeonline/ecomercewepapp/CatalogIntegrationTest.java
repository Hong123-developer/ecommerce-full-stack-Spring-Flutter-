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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the catalogue through the real filter chain: role gating on the admin
 * endpoints, and the optional-filter browse query against a live database.
 */
@SpringBootTest
class CatalogIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DataSeeder dataSeeder;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
    }

    // ---------- authorization ----------

    @Test
    void anonymousCannotCreateACategory() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(category("Anon Attempt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateACategory() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .with(user("shopper").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(category("Customer Attempt")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicBrowseNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    // ---------- catalogue behaviour ----------

    @Test
    void adminCreatesCategoryAndProductThenShopperCanFindIt() throws Exception {
        long categoryId = createCategory("Shoes");
        createProduct(categoryId, "Running Shoe", "SKU-RUN-1", "49.99", 10, true);

        mockMvc.perform(get("/api/v1/products").param("search", "running"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Running Shoe"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Shoes"))
                .andExpect(jsonPath("$.data.content[0].inStock").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void browseFiltersByCategoryAndPriceRange() throws Exception {
        long cheap = createCategory("Cheap");
        long pricey = createCategory("Pricey");
        createProduct(cheap, "Sock", "SKU-SOCK", "3.00", 100, true);
        createProduct(pricey, "Watch", "SKU-WATCH", "250.00", 2, true);

        // category filter alone
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(pricey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Watch"));

        // price ceiling alone
        mockMvc.perform(get("/api/v1/products").param("maxPrice", "10.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Sock"));

        // price floor excludes both
        mockMvc.perform(get("/api/v1/products").param("minPrice", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void invertedPriceRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "100").param("maxPrice", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disabledProductIsHiddenFromShoppersButVisibleToAdmin() throws Exception {
        long categoryId = createCategory("Hidden Goods");
        long productId = createProduct(categoryId, "Secret Item", "SKU-SECRET", "9.99", 5, false);

        mockMvc.perform(get("/api/v1/products/" + productId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/products/" + productId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void disabledCategoryIsHiddenFromShoppers() throws Exception {
        long categoryId = createCategory("Seasonal");
        mockMvc.perform(patch("/api/v1/admin/categories/" + categoryId + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .param("enabled", "false"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories/" + categoryId))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateSkuIsRejected() throws Exception {
        long categoryId = createCategory("Dupes");
        createProduct(categoryId, "First", "SKU-DUPE", "1.00", 1, true);

        mockMvc.perform(adminPost("/api/v1/admin/products",
                        product(categoryId, "Second", "SKU-DUPE", "2.00", 1, true)))
                .andExpect(status().isConflict());
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        long categoryId = createCategory("Occupied");
        createProduct(categoryId, "Occupant", "SKU-OCCUPY", "5.00", 1, true);

        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("still has products")));
    }

    @Test
    void productValidationRejectsNegativePriceAndMissingCategory() throws Exception {
        mockMvc.perform(adminPost("/api/v1/admin/products", """
                        {"name":"Bad","sku":"SKU-BAD","price":-1,"stockQuantity":-5}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.stockQuantity").exists());
    }

    // ---------- helpers ----------

    private long createCategory(String name) throws Exception {
        String body = mockMvc.perform(adminPost("/api/v1/admin/categories", category(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private long createProduct(long categoryId, String name, String sku,
                               String price, int stock, boolean enabled) throws Exception {
        String body = mockMvc.perform(adminPost("/api/v1/admin/products",
                        product(categoryId, name, sku, price, stock, enabled)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private MockHttpServletRequestBuilder adminPost(String path, String json) {
        return post(path)
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private String category(String name) {
        return """
                {"name":"%s","description":"test category"}
                """.formatted(name);
    }

    private String product(long categoryId, String name, String sku,
                           String price, int stock, boolean enabled) {
        return """
                {"categoryId":%d,"name":"%s","sku":"%s","price":%s,"stockQuantity":%d,"enabled":%b}
                """.formatted(categoryId, name, sku, price, stock, enabled);
    }
}
