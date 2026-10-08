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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product photo galleries: ordered extra images, partial updates, validation. */
@SpringBootTest
class ProductGalleryIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String IMG = "http://localhost:8080/uploads/products/";

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
                        .content(json(Map.of("name", "Gallery " + SEQ.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        categoryId = objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> product(List<String> gallery) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", "Phone " + SEQ.incrementAndGet());
        body.put("sku", "SKU-GAL-" + SEQ.incrementAndGet());
        body.put("price", 555.38);
        body.put("stockQuantity", 5);
        body.put("imageUrl", IMG + "main.png");
        if (gallery != null) {
            body.put("galleryUrls", gallery);
        }
        return body;
    }

    private long create(Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    @Test
    void galleryIsStoredInOrderTrimmedAndDeduplicatedAndShownPublicly() throws Exception {
        long id = create(product(List.of(IMG + "b.png", "  " + IMG + "a.png ", IMG + "b.png")));

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(IMG + "main.png"))
                .andExpect(jsonPath("$.data.galleryUrls", contains(IMG + "b.png", IMG + "a.png")));

        // listings carry it too (batch-fetched)
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.data.content[0].galleryUrls", contains(IMG + "b.png", IMG + "a.png")));
    }

    @Test
    void updateWithoutGalleryKeepsItAndEmptyListClearsIt() throws Exception {
        long id = create(product(List.of(IMG + "1.png", IMG + "2.png")));

        // a client that doesn't know about galleries
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(product(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.galleryUrls", contains(IMG + "1.png", IMG + "2.png")));

        // reorder
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(List.of(IMG + "2.png", IMG + "1.png")))))
                .andExpect(jsonPath("$.data.galleryUrls", contains(IMG + "2.png", IMG + "1.png")));

        // clear
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(product(List.of()))))
                .andExpect(jsonPath("$.data.galleryUrls", empty()));
    }

    @Test
    void productWithoutGalleryReturnsAnEmptyList() throws Exception {
        long id = create(product(null));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.galleryUrls", empty()));
    }

    @Test
    void galleryIsValidated() throws Exception {
        List<String> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(IMG + i + ".png");
        }
        mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(product(nine))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(List.of("javascript:alert(1)")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/products").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(List.of("//evil.example/x.png")))))
                .andExpect(status().isBadRequest());
    }
}
