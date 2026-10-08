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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Home-page hero slides through the real filter chain: public read, role gating on the
 * admin endpoints, ordering, reorder, link validation, and the optional accent color.
 * <p>
 * The H2 database is shared with the other test classes, so nothing here assumes an empty
 * table: assertions filter by the ids this class created, and {@link #cleanUp()} deletes them.
 */
@SpringBootTest
class HeroSlideIntegrationTest {

    private static final String PUBLIC = "/api/v1/hero-slides";
    private static final String ADMIN = "/api/v1/admin/hero-slides";
    private static final String LINK_ERROR = "Link must start with / or http(s)://";
    private static final String ORDER_ERROR = "Order must list every slide exactly once";
    private static final String COLOR_ERROR = "Banner color must be a hex color like #D97706";

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() throws Exception {
        // Status is ignored: a test may already have deleted its own slide.
        for (Long id : created) {
            mockMvc.perform(delete(ADMIN + "/" + id).with(admin()));
        }
        created.clear();
    }

    // ---------- authorization ----------

    @Test
    void publicListNeedsNoToken() throws Exception {
        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void anonymousCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(post(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(slide("Anon Attempt"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateASlide() throws Exception {
        mockMvc.perform(post(ADMIN)
                        .with(user("shopper").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(slide("Customer Attempt"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------- listing ----------

    @Test
    void publicListShowsOnlyEnabledSlidesInDisplayOrder() throws Exception {
        long later = createSlide(slide("Later", 7, null));
        long first = createSlide(slide("First", 3, null));
        long tie = createSlide(slide("Tie", 3, null));     // same sort order -> id breaks the tie
        long hidden = createSlide(slide("Hidden", 1, false));
        Set<Long> mine = Set.of(later, first, tie, hidden);

        assertThat(publicIds().stream().filter(mine::contains).toList())
                .containsExactly(first, tie, later);

        // admin sees the disabled one too, in the same ordering
        assertThat(adminIds().stream().filter(mine::contains).toList())
                .containsExactly(hidden, first, tie, later);
    }

    // ---------- create / update ----------

    @Test
    void adminCreateAppendsToTheEndAndNormalisesBlanks() throws Exception {
        int maxBefore = adminSortOrders().stream().mapToInt(Integer::intValue).max().orElse(-1);

        Map<String, Object> body = slide("  Spring Sale  ");
        body.put("eyebrow", "  New season ");
        body.put("subtitle", "");
        body.put("imageAlt", "Model in a red jacket");
        body.put("primaryLabel", "Shop now");
        body.put("primaryLink", "/products");
        body.put("secondaryLabel", "   ");
        body.put("secondaryLink", "https://x.com");

        String response = mockMvc.perform(adminJson(post(ADMIN), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Slide created"))
                .andExpect(jsonPath("$.data.title").value("Spring Sale"))
                .andExpect(jsonPath("$.data.eyebrow").value("New season"))
                .andExpect(jsonPath("$.data.subtitle").value(nullValue()))
                .andExpect(jsonPath("$.data.secondaryLabel").value(nullValue()))
                .andExpect(jsonPath("$.data.primaryLabel").value("Shop now"))
                .andExpect(jsonPath("$.data.primaryLink").value("/products"))
                .andExpect(jsonPath("$.data.secondaryLink").value("https://x.com"))
                .andExpect(jsonPath("$.data.sortOrder").value(maxBefore + 1))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();
        created.add(objectMapper.readTree(response).get("data").get("id").asLong());

        // the next one without a position goes after it
        long next = createSlide(slide("Appended"));
        mockMvc.perform(get(ADMIN + "/" + next).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(maxBefore + 2));
    }

    @Test
    void adminUpdatesASlideAndNullFieldsKeepTheirValue() throws Exception {
        long id = createSlide(slide("Before", 4, false));

        // sortOrder and enabled omitted -> unchanged
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), slide("After")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slide updated"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.title").value("After"))
                .andExpect(jsonPath("$.data.sortOrder").value(4))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(adminJson(put(ADMIN + "/" + id), slide("After", 9, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(9))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(get(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After"));
    }

    @Test
    void togglingStatusHidesAndRestoresTheSlide() throws Exception {
        long id = createSlide(slide("Toggle"));
        assertThat(publicIds()).contains(id);

        mockMvc.perform(patch(ADMIN + "/" + id + "/status").with(admin()).param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slide disabled"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(publicIds()).doesNotContain(id);
        assertThat(adminIds()).contains(id);

        mockMvc.perform(patch(ADMIN + "/" + id + "/status").with(admin()).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slide enabled"));
        assertThat(publicIds()).contains(id);
    }

    // ---------- reorder ----------

    @Test
    void reorderSetsEachSortOrderToItsPosition() throws Exception {
        createSlide(slide("Reorder A"));
        createSlide(slide("Reorder B"));
        List<Long> reversed = new ArrayList<>(adminIds());
        Collections.reverse(reversed);

        String response = mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of("ids", reversed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slide order updated"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).get("data");
        assertThat(data.size()).isEqualTo(reversed.size());
        for (int i = 0; i < data.size(); i++) {
            assertThat(data.get(i).get("id").asLong()).isEqualTo(reversed.get(i));
            assertThat(data.get(i).get("sortOrder").asInt()).isEqualTo(i);
        }
        assertThat(adminIds()).containsExactlyElementsOf(reversed);
    }

    @Test
    void reorderRejectsIncompleteDuplicatedOrUnknownIds() throws Exception {
        createSlide(slide("Order X"));
        createSlide(slide("Order Y"));
        List<Long> all = adminIds();

        List<Long> incomplete = all.subList(0, all.size() - 1);
        List<Long> duplicated = new ArrayList<>(all);
        duplicated.set(duplicated.size() - 1, all.getFirst());
        List<Long> unknown = new ArrayList<>(all);
        unknown.set(unknown.size() - 1, Long.MAX_VALUE);

        for (List<Long> ids : List.of(incomplete, duplicated, unknown)) {
            mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of("ids", ids)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(ORDER_ERROR));
        }

        mockMvc.perform(adminJson(put(ADMIN + "/order"), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ids").exists());

        // a rejected reorder changes nothing
        assertThat(adminIds()).containsExactlyElementsOf(all);
    }

    // ---------- delete / not found ----------

    @Test
    void deletedSlideIsGone() throws Exception {
        long id = createSlide(slide("Short Lived"));

        mockMvc.perform(delete(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slide deleted"));

        mockMvc.perform(get(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isNotFound());
        assertThat(publicIds()).doesNotContain(id);
    }

    @Test
    void unknownSlideIsNotFound() throws Exception {
        String missing = ADMIN + "/" + Long.MAX_VALUE;

        mockMvc.perform(get(missing).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(adminJson(put(missing), slide("Ghost")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(missing + "/status").with(admin()).param("enabled", "true"))
                .andExpect(status().isNotFound());
    }

    // ---------- validation ----------

    @Test
    void floatingCardsAreStoredTrimmedAndClearedWithBlanks() throws Exception {
        Map<String, Object> body = slide("With cards");
        body.put("card1ImageUrl", "http://localhost:8080/uploads/products/card1.png");
        body.put("card1Title", "  Air Max 270 ");
        body.put("card1Caption", "$129.99");
        body.put("card1Link", "/products/12");
        body.put("card2ImageUrl", "http://localhost:8080/uploads/products/card2.png");
        body.put("card2Link", "https://example.com/sale");
        long id = createSlide(body);

        JsonNode saved = list(get(ADMIN + "/" + id).with(admin()));
        assertThat(saved.get("card1Title").asString()).isEqualTo("Air Max 270");
        assertThat(saved.get("card1Caption").asString()).isEqualTo("$129.99");
        assertThat(saved.get("card1Link").asString()).isEqualTo("/products/12");
        assertThat(saved.get("card2ImageUrl").asString()).endsWith("card2.png");
        assertThat(saved.get("card2Title").isNull()).isTrue();

        // The storefront sees the cards too.
        JsonNode shown = null;
        for (JsonNode slide : list(get(PUBLIC))) {
            if (slide.get("id").asLong() == id) {
                shown = slide;
            }
        }
        assertThat(shown).isNotNull();
        assertThat(shown.get("card1ImageUrl").asString()).endsWith("card1.png");

        // Sending blanks clears a card.
        Map<String, Object> cleared = slide("With cards");
        cleared.put("card1ImageUrl", " ");
        cleared.put("card1Title", "");
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), cleared)).andExpect(status().isOk());
        JsonNode after = list(get(ADMIN + "/" + id).with(admin()));
        assertThat(after.get("card1ImageUrl").isNull()).isTrue();
        assertThat(after.get("card1Title").isNull()).isTrue();
    }

    @Test
    void floatingCardLinksAndLengthsAreValidated() throws Exception {
        Map<String, Object> badLink = slide("Bad card link");
        badLink.put("card1ImageUrl", "http://localhost:8080/uploads/products/card.png");
        badLink.put("card1Link", "javascript:alert(1)");
        mockMvc.perform(adminJson(post(ADMIN), badLink))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.card1Link").value("Link must start with / or http(s)://"));

        Map<String, Object> tooLong = slide("Long caption");
        tooLong.put("card2Caption", "x".repeat(41));
        mockMvc.perform(adminJson(post(ADMIN), tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.card2Caption").exists());
    }

    @Test
    void validationRejectsMissingTitleAndImage() throws Exception {
        mockMvc.perform(adminJson(post(ADMIN), Map.of("eyebrow", "No title", "title", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());

        Map<String, Object> negative = slide("Negative");
        negative.put("sortOrder", -1);
        mockMvc.perform(adminJson(post(ADMIN), negative))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sortOrder").exists());
    }

    @Test
    void validationRejectsLinksThatAreNotPathsOrHttp() throws Exception {
        Map<String, Object> script = slide("Script");
        script.put("primaryLink", "javascript:alert(1)");
        script.put("secondaryLink", "//evil.example");
        mockMvc.perform(adminJson(post(ADMIN), script))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.primaryLink").value(LINK_ERROR))
                .andExpect(jsonPath("$.fieldErrors.secondaryLink").value(LINK_ERROR));

        // browsers treat "/\host" like "//host"
        Map<String, Object> backslash = slide("Backslash");
        backslash.put("primaryLink", "/\\evil.example");
        mockMvc.perform(adminJson(post(ADMIN), backslash))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.primaryLink").value(LINK_ERROR));
    }

    // ---------- accent color ----------

    @Test
    void accentColorIsStoredUpperCaseAndShownOnAdminAndStorefront() throws Exception {
        Map<String, Object> body = slide("Amber");
        body.put("accentColor", "#d97706");

        String response = mockMvc.perform(adminJson(post(ADMIN), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accentColor").value("#D97706"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("data").get("id").asLong();
        created.add(id);

        mockMvc.perform(get(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accentColor").value("#D97706"));
        assertThat(slideIn(list(get(ADMIN).with(admin())), id).get("accentColor").asString())
                .isEqualTo("#D97706");
        assertThat(slideIn(list(get(PUBLIC)), id).get("accentColor").asString())
                .isEqualTo("#D97706");

        // Toggling the status leaves the color alone.
        mockMvc.perform(patch(ADMIN + "/" + id + "/status").with(admin()).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accentColor").value("#D97706"));

        // An update with mixed case is normalised the same way.
        Map<String, Object> recolored = slide("Amber");
        recolored.put("accentColor", "#1e40Af");
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), recolored))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accentColor").value("#1E40AF"));
        assertThat(slideIn(list(get(PUBLIC)), id).get("accentColor").asString())
                .isEqualTo("#1E40AF");
    }

    @Test
    void accentColorMustBeASixDigitHexColor() throws Exception {
        for (String bad : List.of("orange", "#12345", "#GGGGGG", "D97706", "#FFF", "#D977061")) {
            Map<String, Object> body = slide("Bad color");
            body.put("accentColor", bad);
            mockMvc.perform(adminJson(post(ADMIN), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.fieldErrors.accentColor").value(COLOR_ERROR));
        }

        // Updates are validated too, and a rejected one keeps the stored color.
        Map<String, Object> good = slide("Good color");
        good.put("accentColor", "#D97706");
        long id = createSlide(good);

        Map<String, Object> bad = slide("Good color");
        bad.put("accentColor", "orange");
        mockMvc.perform(adminJson(put(ADMIN + "/" + id), bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accentColor").value(COLOR_ERROR));
        mockMvc.perform(get(ADMIN + "/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accentColor").value("#D97706"));
    }

    @Test
    void blankAccentColorIsStoredAsNullAndAnUpdateToNullClearsIt() throws Exception {
        Map<String, Object> blank = slide("Clear color");
        blank.put("accentColor", "   ");
        long id = createSlide(blank);
        assertNoAccent(list(get(ADMIN + "/" + id).with(admin())));

        Map<String, Object> colored = slide("Clear color");
        colored.put("accentColor", "#1E40AF");

        // Same rule as eyebrow and the other optional text fields: on update, an explicit
        // null, a blank, or leaving the field out all store null.
        Map<String, Object> explicitNull = slide("Clear color");
        explicitNull.put("accentColor", null);
        Map<String, Object> empty = slide("Clear color");
        empty.put("accentColor", "");
        Map<String, Object> omitted = slide("Clear color");

        for (Map<String, Object> clearing : List.of(explicitNull, empty, omitted)) {
            mockMvc.perform(adminJson(put(ADMIN + "/" + id), colored))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accentColor").value("#1E40AF"));

            mockMvc.perform(adminJson(put(ADMIN + "/" + id), clearing))
                    .andExpect(status().isOk());
            assertNoAccent(list(get(ADMIN + "/" + id).with(admin())));
            assertNoAccent(slideIn(list(get(PUBLIC)), id));
        }
    }

    @Test
    void slidesWithoutAColorSerializeAccentColorAsNull() throws Exception {
        // A row from before the column existed: the insert does not name accent_color at all.
        String legacyTitle = "Legacy " + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO hero_slides (title, image_url, sort_order, enabled, created_at, updated_at)
                VALUES (?, ?, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
                legacyTitle, "http://localhost:8080/uploads/products/hero.png");
        Long legacy = jdbcTemplate.queryForObject(
                "SELECT id FROM hero_slides WHERE title = ?", Long.class, legacyTitle);
        created.add(legacy);

        // And one from a client that does not know the field.
        long plain = createSlide(slide("No color"));

        for (long id : List.of(legacy, plain)) {
            assertNoAccent(list(get(ADMIN + "/" + id).with(admin())));
            assertNoAccent(slideIn(list(get(ADMIN).with(admin())), id));
            assertNoAccent(slideIn(list(get(PUBLIC)), id));
        }
    }

    // ---------- helpers ----------

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private MockHttpServletRequestBuilder adminJson(MockHttpServletRequestBuilder request,
                                                    Object body) {
        return request.with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    /** Minimal valid slide body; mutable so a test can add fields. */
    private static Map<String, Object> slide(String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("imageUrl", "http://localhost:8080/uploads/products/hero.png");
        return body;
    }

    private static Map<String, Object> slide(String title, Integer sortOrder, Boolean enabled) {
        Map<String, Object> body = slide(title);
        if (sortOrder != null) {
            body.put("sortOrder", sortOrder);
        }
        if (enabled != null) {
            body.put("enabled", enabled);
        }
        return body;
    }

    private long createSlide(Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(adminJson(post(ADMIN), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("data").get("id").asLong();
        created.add(id);
        return id;
    }

    private JsonNode list(MockHttpServletRequestBuilder request) throws Exception {
        String response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    /** The slide with {@code id} in a listing; fails the test when it is not there. */
    private static JsonNode slideIn(JsonNode slides, long id) {
        for (JsonNode slide : slides) {
            if (slide.get("id").asLong() == id) {
                return slide;
            }
        }
        throw new AssertionError("Slide " + id + " is not in the listing");
    }

    /** The key is present and JSON null - not merely missing - so clients can rely on it. */
    private static void assertNoAccent(JsonNode slide) {
        JsonNode color = slide.get("accentColor");
        assertThat(color).as("accentColor key of slide %s", slide.get("id")).isNotNull();
        assertThat(color.isNull()).as("accentColor of slide %s is null", slide.get("id")).isTrue();
    }

    private List<Long> ids(JsonNode slides) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            ids.add(slides.get(i).get("id").asLong());
        }
        return ids;
    }

    private List<Long> publicIds() throws Exception {
        return ids(list(get(PUBLIC)));
    }

    private List<Long> adminIds() throws Exception {
        return ids(list(get(ADMIN).with(admin())));
    }

    private List<Integer> adminSortOrders() throws Exception {
        JsonNode slides = list(get(ADMIN).with(admin()));
        List<Integer> orders = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            orders.add(slides.get(i).get("sortOrder").asInt());
        }
        return orders;
    }
}
