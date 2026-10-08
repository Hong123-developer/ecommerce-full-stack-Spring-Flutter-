package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin image upload: role gating, content sniffing (the declared type is ignored),
 * and that a stored image is served back publicly.
 */
@SpringBootTest
class ImageUploadIntegrationTest {

    /** Smallest valid PNG signature + IHDR start; enough for the magic-byte check. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    @Test
    void anonymousCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/uploads/images").file(file("a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/uploads/images")
                        .file(file("a.png", "image/png", PNG))
                        .with(user("shopper").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUploadsPngAndItIsServedPublicly() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/uploads/images")
                        .file(file("photo.png", "image/png", PNG))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.path").value(org.hamcrest.Matchers.matchesPattern(
                        "/uploads/products/[0-9a-f-]{36}\\.png")))
                .andReturn();

        String path = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("path").asString();
        assertThat(result.getResponse().getContentAsString()).contains("http://localhost" + path);

        // No token: product images are public, like the catalogue.
        MvcResult served = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void nonImageIsRejectedEvenWhenItClaimsToBeAnImage() throws Exception {
        byte[] script = "<script>alert(1)</script>".getBytes();
        mockMvc.perform(multipart("/api/v1/admin/uploads/images")
                        .file(file("evil.png", "image/png", script))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPG, PNG, WebP or GIF images are allowed"));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/uploads/images")
                        .file(file("empty.png", "image/png", new byte[0]))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFilePartIsA400() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/uploads/images").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }
}
