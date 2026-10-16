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
 * Category display order: admin move up / move down, new categories at the end, the
 * alphabetical fallback for categories from before ordering existed, and the storefront
 * listing that follows the same order.
 * <p>
 * The H2 database is shared with the other test classes, so nothing here assumes an empty
 * table: names share a unique "zz-order-" prefix (sorting after everything else when positions
 * tie), assertions look at the categories this class created, and {@link #cleanUp()} deletes them.
 */
@SpringBootTest
class CategoryOrderIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/categories";
    private static final String PUBLIC = "/api/v1/categories";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    private final List<Long> created = new ArrayList<>();
    private String prefix;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "zz-order-" + System.nanoTime() + "-";
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Long id : created) {
            mockMvc.perform(delete(ADMIN + "/" + id).with(admin()));
        }
        created.clear();
    }

    // ---------- authorization ----------

    @Test
    void onlyAdminsCanMoveCategories() throws Exception {
        long id = createCategory("Guarded", true);

        mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", "UP"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", "UP")
                        .with(user("shopper").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---------- ordering ----------

    @Test
    void newCategoriesGoToTheEnd() throws Exception {
        long a = createCategory("A", true);
        long b = createCategory("B", true);
        long c = createCategory("C", true);

        List<Long> all = adminIds();
        assertThat(all.subList(all.size() - 3, all.size())).containsExactly(a, b, c);
    }

    @Test
    void moveUpAndDownSwapNeighbours() throws Exception {
        long a = createCategory("A", true);
        long b = createCategory("B", true);
        long c = createCategory("C", true);

        move(c, "UP").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(c))
                .andExpect(jsonPath("$.message").value("Category moved up"));
        assertThat(mine(adminIds())).containsExactly(a, c, b);

        move(a, "DOWN").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Category moved down"));
        assertThat(mine(adminIds())).containsExactly(c, a, b);
    }

    @Test
    void cannotMovePastEitherEnd() throws Exception {
        long last = createCategory("Last", true);

        move(last, "DOWN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This category is already last"));

        List<Long> all = adminIds();
        move(all.get(0), "UP").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This category is already first"));

        assertThat(adminIds()).isEqualTo(all);
    }

    @Test
    void unknownCategoryOrDirectionIsRejected() throws Exception {
        move(987_654_321L, "UP").andExpect(status().isNotFound());

        long id = createCategory("Directionless", true);
        move(id, "SIDEWAYS").andExpect(status().isBadRequest());
    }

    @Test
    void tiedLegacyPositionsFallBackToTheNameAndAreRenumberedOnTheFirstMove() throws Exception {
        long zulu = createCategory("Zulu", true);
        long alpha = createCategory("Alpha", true);
        long mike = createCategory("Mike", true);
        // What an existing database looks like right after the column is added.
        jdbcTemplate.update("UPDATE categories SET sort_order = 0");
        assertThat(mine(adminIds())).containsExactly(alpha, mike, zulu);

        move(zulu, "UP").andExpect(status().isOk());

        assertThat(mine(adminIds())).containsExactly(alpha, zulu, mike);
        Integer distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT sort_order) FROM categories", Integer.class);
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        assertThat(distinct).isEqualTo(total);

        // ...after which a plain two-row swap keeps everything else where it was.
        List<Long> before = adminIds();
        move(alpha, "DOWN").andExpect(status().isOk());
        List<Long> expected = new ArrayList<>(before);
        int at = before.indexOf(alpha);
        expected.set(at, zulu);
        expected.set(at + 1, alpha);
        assertThat(adminIds()).isEqualTo(expected);
    }

    @Test
    void storefrontFollowsTheAdminOrderAndHidesDisabled() throws Exception {
        long a = createCategory("A", true);
        long b = createCategory("B", true);
        long hidden = createCategory("Hidden", false);
        move(b, "UP").andExpect(status().isOk());

        assertThat(mine(adminIds())).containsExactly(b, a, hidden);
        assertThat(mine(ids(PUBLIC + "?size=1000"))).containsExactly(b, a);
        // Explicit sorts still win.
        assertThat(mine(ids(PUBLIC + "?size=1000&sort=name,desc"))).containsExactly(b, a);
        assertThat(mine(ids(PUBLIC + "?size=1000&sort=name,asc"))).containsExactly(a, b);
    }

    @Test
    void pagesDoNotRepeatOrSkipTiedCategories() throws Exception {
        for (int i = 0; i < 4; i++) {
            createCategory("Tied " + i, true);
        }
        jdbcTemplate.update("UPDATE categories SET sort_order = 0");

        JsonNode first = page(PUBLIC + "?size=1&page=0");
        int totalPages = first.get("totalPages").asInt();
        List<Long> paged = new ArrayList<>();
        for (int page = 0; page < totalPages; page++) {
            paged.addAll(ids(PUBLIC + "?size=1&page=" + page));
        }
        assertThat(paged).hasSize(first.get("totalElements").asInt()).doesNotHaveDuplicates();
        assertThat(paged).containsAll(created);
    }

    // ---------- helpers ----------

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private ResultActions move(long id, String direction) throws Exception {
        return mockMvc.perform(patch(ADMIN + "/" + id + "/move").param("direction", direction).with(admin()));
    }

    private long createCategory(String name, boolean enabled) throws Exception {
        String body = mockMvc.perform(post(ADMIN).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"display order test","enabled":%b}
                                """.formatted(prefix + name, enabled)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("data").get("id").asLong();
        created.add(id);
        return id;
    }

    /** Every category, in the admin list's default (display) order. */
    private List<Long> adminIds() throws Exception {
        String body = mockMvc.perform(get(ADMIN).param("size", "1000").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ids(objectMapper.readTree(body).get("data"));
    }

    private JsonNode page(String url) throws Exception {
        String body = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private List<Long> ids(String url) throws Exception {
        return ids(page(url));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(category -> ids.add(category.get("id").asLong()));
        return ids;
    }

    /** The ids this test created, in the order they appear in {@code all}. */
    private List<Long> mine(List<Long> all) {
        Set<Long> own = Set.copyOf(created);
        return all.stream().filter(own::contains).toList();
    }
}
