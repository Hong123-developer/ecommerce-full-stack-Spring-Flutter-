package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.entity.StoreSettings;
import com.ecomerceshopeonline.ecomercewepapp.repository.StoreSettingsRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin-editable store branding through the real filter chain: public read with defaults
 * before the first save, role gating, the single-row upsert, blank normalisation and
 * validation.
 * <p>
 * The H2 database is shared with the other test classes, so the settings row is deleted
 * before and after every test - "nothing saved yet" then holds whatever ran first.
 */
@SpringBootTest
class StoreSettingsIntegrationTest {

    private static final String PUBLIC = "/api/v1/store-settings";
    private static final String ADMIN = "/api/v1/admin/store-settings";
    private static final String UPLOAD = "/api/v1/admin/uploads/images";

    private static final List<String> FIELDS = List.of(
            "storeName", "logoUrl", "showNameWithLogo", "tagline",
            "supportEmail", "supportPhone", "address", "updatedAt");

    /** Smallest valid PNG signature + IHDR start; enough for the upload's magic-byte check. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoreSettingsRepository storeSettingsRepository;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        storeSettingsRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        storeSettingsRepository.deleteAll();
    }

    // ---------- defaults before the first save ----------

    @Test
    void publicGetNeedsNoTokenAndReturnsDefaultsWhenNothingIsSaved() throws Exception {
        JsonNode data = data(mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn().getResponse().getContentAsString());

        assertUnsavedDefaults(data);
    }

    @Test
    void adminGetReturnsTheSameDefaultsWhenNothingIsSaved() throws Exception {
        JsonNode data = data(mockMvc.perform(get(ADMIN).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString());

        assertUnsavedDefaults(data);
        assertThat(storeSettingsRepository.count()).isZero();
    }

    // ---------- authorization ----------

    @Test
    void anonymousCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());

        mockMvc.perform(put(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(settings("Anon Attempt"))))
                .andExpect(status().isUnauthorized());

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void customerCannotSaveOrReadAdminSettings() throws Exception {
        RequestPostProcessor customer = user("shopper").roles("CUSTOMER");

        mockMvc.perform(put(ADMIN)
                        .with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(settings("Customer Attempt"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get(ADMIN).with(customer)).andExpect(status().isForbidden());

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void publicEndpointIsReadOnly() throws Exception {
        mockMvc.perform(put(PUBLIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(settings("Sneaky"))))
                .andExpect(status().isUnauthorized());

        assertThat(storeSettingsRepository.count()).isZero();
    }

    // ---------- save ----------

    @Test
    void adminSaveIsReflectedOnThePublicEndpoint() throws Exception {
        mockMvc.perform(adminPut(fullSettings()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Store settings saved"))
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend"))
                .andExpect(jsonPath("$.data.logoUrl").value("http://localhost:8080/uploads/products/logo.png"))
                .andExpect(jsonPath("$.data.showNameWithLogo").value(false))
                .andExpect(jsonPath("$.data.tagline").value("Style that moves with you"))
                .andExpect(jsonPath("$.data.supportEmail").value("support@novatrend.test"))
                .andExpect(jsonPath("$.data.supportPhone").value("+855 (12) 345-678"))
                .andExpect(jsonPath("$.data.address").value("Phnom Penh, Cambodia"))
                .andExpect(jsonPath("$.data.updatedAt").value(notNullValue()));

        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend"))
                .andExpect(jsonPath("$.data.logoUrl").value("http://localhost:8080/uploads/products/logo.png"))
                .andExpect(jsonPath("$.data.showNameWithLogo").value(false))
                .andExpect(jsonPath("$.data.tagline").value("Style that moves with you"))
                .andExpect(jsonPath("$.data.supportEmail").value("support@novatrend.test"))
                .andExpect(jsonPath("$.data.supportPhone").value("+855 (12) 345-678"))
                .andExpect(jsonPath("$.data.address").value("Phnom Penh, Cambodia"))
                .andExpect(jsonPath("$.data.updatedAt").value(notNullValue()));

        mockMvc.perform(get(ADMIN).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend"));

        assertThat(storeSettingsRepository.count()).isEqualTo(1);
        assertThat(storeSettingsRepository.findAll().getFirst().getId())
                .isEqualTo(StoreSettings.SINGLETON_ID);
    }

    @Test
    void secondSaveUpdatesTheSameSingleRow() throws Exception {
        mockMvc.perform(adminPut(fullSettings())).andExpect(status().isOk());
        StoreSettings first = storeSettingsRepository.findSingleton().orElseThrow();

        Map<String, Object> changed = fullSettings();
        changed.put("storeName", "Nova Trend Outlet");
        changed.put("showNameWithLogo", true);
        changed.put("tagline", "Now with more sale");
        mockMvc.perform(adminPut(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend Outlet"))
                .andExpect(jsonPath("$.data.showNameWithLogo").value(true))
                .andExpect(jsonPath("$.data.tagline").value("Now with more sale"));

        assertThat(storeSettingsRepository.count()).isEqualTo(1);
        StoreSettings second = storeSettingsRepository.findSingleton().orElseThrow();
        assertThat(second.getId()).isEqualTo(StoreSettings.SINGLETON_ID);
        assertThat(second.getStoreName()).isEqualTo("Nova Trend Outlet");
        // still the row created by the first save, not a replacement
        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(second.getUpdatedAt()).isAfterOrEqualTo(first.getUpdatedAt());

        mockMvc.perform(get(PUBLIC))
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend Outlet"))
                .andExpect(jsonPath("$.data.tagline").value("Now with more sale"));
    }

    @Test
    void databaseRefusesASecondSettingsRow() {
        StoreSettings rogue = StoreSettings.builder().id(2L).storeName("Second Row").build();

        assertThatThrownBy(() -> storeSettingsRepository.saveAndFlush(rogue))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void updateResponseCarriesTheFreshUpdatedAt() throws Exception {
        mockMvc.perform(adminPut(fullSettings())).andExpect(status().isOk());
        LocalDateTime firstStored = storeSettingsRepository.findSingleton().orElseThrow().getUpdatedAt();
        // Two saves inside one clock tick would share a timestamp (Windows' clock is coarse),
        // which would make "strictly after" fail for reasons that have nothing to do with the code.
        Thread.sleep(20);

        Map<String, Object> changed = fullSettings();
        changed.put("tagline", "Fresh timestamp please");
        JsonNode data = data(mockMvc.perform(adminPut(changed))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        LocalDateTime returned = LocalDateTime.parse(data.get("updatedAt").asString());
        LocalDateTime stored = storeSettingsRepository.findSingleton().orElseThrow().getUpdatedAt();
        // The column keeps microseconds, the in-memory value may carry more digits.
        assertThat(returned).isCloseTo(stored, within(1, ChronoUnit.MICROS));
        assertThat(stored).isAfter(firstStored);
    }

    @Test
    void blanksAreStoredAsNullAndShowNameDefaultsToTrue() throws Exception {
        Map<String, Object> body = settings("  Nova Trend  ");
        body.put("logoUrl", "");
        body.put("showNameWithLogo", null);
        body.put("tagline", "   ");
        body.put("supportEmail", "");
        body.put("supportPhone", "  ");
        body.put("address", "\t");

        mockMvc.perform(adminPut(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend"))
                .andExpect(jsonPath("$.data.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.showNameWithLogo").value(true))
                .andExpect(jsonPath("$.data.tagline").value(nullValue()))
                .andExpect(jsonPath("$.data.supportEmail").value(nullValue()))
                .andExpect(jsonPath("$.data.supportPhone").value(nullValue()))
                .andExpect(jsonPath("$.data.address").value(nullValue()));

        StoreSettings stored = storeSettingsRepository.findSingleton().orElseThrow();
        assertThat(stored.getStoreName()).isEqualTo("Nova Trend");
        assertThat(stored.getLogoUrl()).isNull();
        assertThat(stored.isShowNameWithLogo()).isTrue();
        assertThat(stored.getTagline()).isNull();
        assertThat(stored.getSupportEmail()).isNull();
        assertThat(stored.getSupportPhone()).isNull();
        assertThat(stored.getAddress()).isNull();
    }

    @Test
    void saveIsAFullReplacementSoOmittedFieldsAreCleared() throws Exception {
        mockMvc.perform(adminPut(fullSettings())).andExpect(status().isOk());

        // only the required field: everything else is cleared, showNameWithLogo back to true
        JsonNode data = data(mockMvc.perform(adminPut(settings("Just A Name")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(data.get("storeName").asString()).isEqualTo("Just A Name");
        assertThat(data.get("showNameWithLogo").asBoolean()).isTrue();
        for (String field : List.of("logoUrl", "tagline", "supportEmail", "supportPhone", "address")) {
            assertThat(data.get(field).isNull()).as(field).isTrue();
        }
        assertThat(storeSettingsRepository.count()).isEqualTo(1);
    }

    @Test
    void uploadedImageUrlIsAcceptedAsTheLogo() throws Exception {
        String upload = mockMvc.perform(multipart(UPLOAD)
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .with(admin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = data(upload).get("url").asString();

        Map<String, Object> body = settings("Nova Trend");
        body.put("logoUrl", url);
        mockMvc.perform(adminPut(body)).andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC))
                .andExpect(jsonPath("$.data.logoUrl").value(url));
        // the logo itself is served without a token, like product images
        mockMvc.perform(get(data(upload).get("path").asString()))
                .andExpect(status().isOk());
    }

    @Test
    void relativeLogoPathIsAccepted() throws Exception {
        Map<String, Object> body = settings("Nova Trend");
        body.put("logoUrl", "/uploads/products/logo.webp");

        mockMvc.perform(adminPut(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value("/uploads/products/logo.webp"));
    }

    // ---------- validation ----------

    @Test
    void validationRejectsMissingOrBlankStoreName() throws Exception {
        Map<String, Object> missing = fullSettings();
        missing.remove("storeName");
        mockMvc.perform(adminPut(missing))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors.storeName").value("Store name is required"));

        mockMvc.perform(adminPut(settings("    ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.storeName").value("Store name is required"));

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void storeNameLengthIsCheckedAfterTrimming() throws Exception {
        String sixty = "N".repeat(60);

        mockMvc.perform(adminPut(settings("   " + sixty + "   ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value(sixty));

        mockMvc.perform(adminPut(settings(sixty + "N")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.storeName")
                        .value("Store name must be at most 60 characters"));
    }

    @Test
    void validationRejectsOverlongOptionalFields() throws Exception {
        Map<String, Object> body = settings("Nova Trend");
        body.put("logoUrl", "https://cdn.example.com/" + "a".repeat(477));       // 501
        body.put("tagline", "t".repeat(201));
        // syntactically valid, only too long: 60 + 1 + 62 = 123
        body.put("supportEmail", "a".repeat(60) + "@" + "b".repeat(50) + ".example.com");
        body.put("address", "x".repeat(201));

        mockMvc.perform(adminPut(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.logoUrl").value("Logo URL must be at most 500 characters"))
                .andExpect(jsonPath("$.fieldErrors.tagline").value("Tagline must be at most 200 characters"))
                .andExpect(jsonPath("$.fieldErrors.supportEmail")
                        .value("Support email must be at most 120 characters"))
                .andExpect(jsonPath("$.fieldErrors.address").value("Address must be at most 200 characters"))
                .andExpect(jsonPath("$.fieldErrors.storeName").doesNotExist());

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void validationRejectsBadEmailAndPhone() throws Exception {
        Map<String, Object> body = settings("Nova Trend");
        body.put("supportEmail", "not-an-email");
        body.put("supportPhone", "call me");
        mockMvc.perform(adminPut(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.supportEmail").value("Support email must be a valid address"))
                .andExpect(jsonPath("$.fieldErrors.supportPhone")
                        .value("Support phone must be 6-30 digits, spaces or + ( ) -"));

        for (String phone : List.of("12345", "1".repeat(31), "+855 12 345 678 ext 9")) {
            Map<String, Object> badPhone = settings("Nova Trend");
            badPhone.put("supportPhone", phone);
            mockMvc.perform(adminPut(badPhone))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.supportPhone").exists());
        }

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void validationRejectsLogoUrlsThatAreNotPathsOrHttp() throws Exception {
        for (String logo : List.of("javascript:alert(1)", "//evil.example/logo.png",
                "/\\evil.example/logo.png", "data:image/png;base64,AAAA", "logo.png")) {
            Map<String, Object> body = settings("Nova Trend");
            body.put("logoUrl", logo);
            mockMvc.perform(adminPut(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.logoUrl")
                            .value("Logo URL must start with / or http(s)://"));
        }

        assertThat(storeSettingsRepository.count()).isZero();
    }

    @Test
    void rejectedSaveLeavesTheSavedSettingsUntouched() throws Exception {
        mockMvc.perform(adminPut(fullSettings())).andExpect(status().isOk());

        Map<String, Object> invalid = fullSettings();
        invalid.put("storeName", "Should Not Stick");
        invalid.put("supportEmail", "broken@");
        mockMvc.perform(adminPut(invalid)).andExpect(status().isBadRequest());

        mockMvc.perform(get(PUBLIC))
                .andExpect(jsonPath("$.data.storeName").value("Nova Trend"))
                .andExpect(jsonPath("$.data.supportEmail").value("support@novatrend.test"));
    }

    @Test
    void malformedBodyIsA400() throws Exception {
        mockMvc.perform(put(ADMIN)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(storeSettingsRepository.count()).isZero();
    }

    // ---------- helpers ----------

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private MockHttpServletRequestBuilder adminPut(Object body) {
        return put(ADMIN).with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode data(String response) {
        return objectMapper.readTree(response).get("data");
    }

    /** Every field present - not omitted - and null, except showNameWithLogo = true. */
    private static void assertUnsavedDefaults(JsonNode data) {
        for (String field : FIELDS) {
            assertThat(data.has(field)).as("field %s is present", field).isTrue();
        }
        assertThat(data.size()).isEqualTo(FIELDS.size());
        for (String field : FIELDS) {
            if (!field.equals("showNameWithLogo")) {
                assertThat(data.get(field).isNull()).as("field %s is null", field).isTrue();
            }
        }
        assertThat(data.get("showNameWithLogo").isBoolean()).isTrue();
        assertThat(data.get("showNameWithLogo").asBoolean()).isTrue();
    }

    /** Minimal valid body; mutable so a test can add fields. */
    private static Map<String, Object> settings(String storeName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeName", storeName);
        return body;
    }

    private static Map<String, Object> fullSettings() {
        Map<String, Object> body = settings("Nova Trend");
        body.put("logoUrl", "http://localhost:8080/uploads/products/logo.png");
        body.put("showNameWithLogo", false);
        body.put("tagline", "Style that moves with you");
        body.put("supportEmail", "support@novatrend.test");
        body.put("supportPhone", "+855 (12) 345-678");
        body.put("address", "Phnom Penh, Cambodia");
        return body;
    }
}
