package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product display order: admin move up / move down, new products on top, and the storefront
 * listing that follows the same order.
 * <p>
 * The H2 database is shared with the other test classes, so nothing here assumes an empty
 * table: assertions look at the products this class created (which always sit on top, being
 * the newest), and {@link #cleanUp()} deletes them again.
 */
@SpringBootTest
class ProductOrderIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/products";
    private static final String PUBLIC = "/api/v1/products";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    private final List<Long> createdProducts = new ArrayList<>();
    private Long categoryId;
    private int skuCounter;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        categoryId = data(mockMvc.perform(adminJson(post("/api/v1/admin/categories"), """
                        {"name":"Order Test %d","description":"display order test"}
                        """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Long id : createdProducts) {
            mockMvc.perform(delete(ADMIN + "/" + id).with(admin()));
        }
        createdProducts.clear();
        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId).with(admin()));
    }

    // ---------- authorization ----------

    @Test
    void onlyAdminsCanMoveProducts() throws Exception {
        long id = createProduct("Guarded", "9.00");

        mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", "DOWN"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", "DOWN")
                        .with(user("shopper").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---------- ordering ----------

    @Test
    void newProductsGoOnTopOfTheAdminList() throws Exception {
        long first = createProduct("First", "1.00");
        long second = createProduct("Second", "2.00");
        long third = createProduct("Third", "3.00");

        assertThat(adminIds(3)).containsExactly(third, second, first);
    }

    @Test
    void moveUpAndDownSwapNeighbours() throws Exception {
        long a = createProduct("A", "1.00");
        long b = createProduct("B", "2.00");
        long c = createProduct("C", "3.00");
        // newest first: C, B, A

        move(a, "UP").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(a))
                .andExpect(jsonPath("$.message").value("Product moved up"));
        assertThat(adminIds(3)).containsExactly(c, a, b);

        move(c, "DOWN").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product moved down"));
        assertThat(adminIds(3)).containsExactly(a, c, b);

        move(b, "UP").andExpect(status().isOk());
        move(b, "UP").andExpect(status().isOk());
        assertThat(adminIds(3)).containsExactly(b, a, c);
    }

    @Test
    void cannotMovePastEitherEnd() throws Exception {
        long top = createProduct("Top", "1.00");

        move(top, "UP").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is already first"));

        List<Long> all = adminIds(1000);
        long bottom = all.get(all.size() - 1);
        move(bottom, "DOWN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This product is already last"));

        assertThat(adminIds(1000)).isEqualTo(all);
    }

    @Test
    void unknownProductOrDirectionIsRejected() throws Exception {
        move(987_654_321L, "UP").andExpect(status().isNotFound());

        long id = createProduct("Directionless", "1.00");
        move(id, "SIDEWAYS").andExpect(status().isBadRequest());
    }

    @Test
    void tiedLegacyPositionsAreRenumberedOnTheFirstMove() throws Exception {
        long a = createProduct("Legacy A", "1.00");
        long b = createProduct("Legacy B", "2.00");
        long c = createProduct("Legacy C", "3.00");
        // What an existing database looks like right after the column is added.
        jdbcTemplate.update("UPDATE products SET sort_order = 0");
        assertThat(adminIds(3)).containsExactly(c, b, a);

        move(a, "UP").andExpect(status().isOk());

        assertThat(adminIds(3)).containsExactly(c, a, b);
        Integer distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT sort_order) FROM products", Integer.class);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        assertThat(distinct).isEqualTo(total);

        // ...after which a plain two-row swap keeps everything else where it was.
        List<Long> before = adminIds(1000);
        move(c, "DOWN").andExpect(status().isOk());
        List<Long> expected = new ArrayList<>(before);
        expected.set(0, a);
        expected.set(1, c);
        assertThat(adminIds(1000)).isEqualTo(expected);
    }

    @Test
    void storefrontFollowsTheAdminOrder() throws Exception {
        long cheap = createProduct("Cheap", "1.00");
        long pricey = createProduct("Pricey", "50.00");
        long middle = createProduct("Middle", "10.00");
        move(cheap, "UP").andExpect(status().isOk());
        move(cheap, "UP").andExpect(status().isOk());
        // admin order now: Cheap, Middle, Pricey

        assertThat(publicIds(PUBLIC + "?categoryId=" + categoryId))
                .containsExactly(cheap, middle, pricey);
        assertThat(publicIds(PUBLIC + "?categoryId=" + categoryId + "&sort=sortOrder,asc"))
                .containsExactly(cheap, middle, pricey);
        // Explicit sorts still win.
        assertThat(publicIds(PUBLIC + "?categoryId=" + categoryId + "&sort=price,desc"))
                .containsExactly(pricey, middle, cheap);
    }

    @Test
    void pagesDoNotRepeatOrSkipTiedProducts() throws Exception {
        for (int i = 0; i < 5; i++) {
            createProduct("Tied " + i, "5.00");
        }
        jdbcTemplate.update("UPDATE products SET sort_order = 0");

        Set<Long> mine = Set.copyOf(createdProducts);
        List<Long> paged = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            paged.addAll(publicIds(PUBLIC + "?categoryId=" + categoryId + "&size=1&page=" + page
                    + "&sort=sortOrder,asc"));
        }
        assertThat(paged).hasSize(5).doesNotHaveDuplicates();
        assertThat(Set.copyOf(paged)).isEqualTo(mine);
        // Ties fall back to the newest product first.
        assertThat(paged).isSortedAccordingTo((x, y) -> Long.compare(y, x));
    }

    // ---------- helpers ----------

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private MockHttpServletRequestBuilder adminJson(MockHttpServletRequestBuilder request, String json) {
        return request.with(admin()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private ResultActions move(long id, String direction) throws Exception {
        return mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", direction).with(admin()));
    }

    private long createProduct(String name, String price) throws Exception {
        String sku = "SKU-ORDER-" + System.nanoTime() + "-" + (skuCounter++);
        long id = data(mockMvc.perform(adminJson(post(ADMIN), """
                        {"categoryId":%d,"name":"%s","sku":"%s","price":%s,"stockQuantity":3,"enabled":true}
                        """.formatted(categoryId, name, sku, price)))
                .andExpect(status().isCreated())).get("id").asLong();
        createdProducts.add(id);
        return id;
    }

    private List<Long> adminIds(int size) throws Exception {
        return ids(data(mockMvc.perform(get(ADMIN).param("size", String.valueOf(size)).with(admin()))
                .andExpect(status().isOk())));
    }

    private List<Long> publicIds(String url) throws Exception {
        return ids(data(mockMvc.perform(get(url)).andExpect(status().isOk())));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(product -> ids.add(product.get("id").asLong()));
        return ids;
    }

    private JsonNode data(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }
}
