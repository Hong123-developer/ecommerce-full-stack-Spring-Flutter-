package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the whole Phase 1 flow through the real filter chain:
 * register -> verify email -> login -> call a protected endpoint -> refresh -> logout.
 */
@SpringBootTest
class AuthFlowIntegrationTest {

    private static final String EMAIL = "customer@test.local";
    private static final String PASSWORD = "Secret123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DataSeeder dataSeeder;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    /** Mocked so no SMTP connection is attempted; also lets us read the generated OTP. */
    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        // ApplicationRunner beans do not fire under @SpringBootTest, so run the seeder by hand.
        dataSeeder.run(null);
    }

    @Test
    void seederCreatesBothRolesAndAnAdmin() {
        assertThat(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).isPresent();
        assertThat(roleRepository.findByName(RoleName.ROLE_ADMIN)).isPresent();
        assertThat(userRepository.findByEmail("admin@test.local")).isPresent();
    }

    @Test
    void protectedEndpointRejectsAnonymousCallerWithJson401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void publicEndpointIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    /**
     * Regression: the catch-all exception handler used to turn any unknown URL into a 500.
     * Anonymous callers get 401 from the filter chain before routing, so this has to be
     * an authenticated request to reach handler mapping at all.
     */
    @Test
    void unknownEndpointReturns404NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist").with(user("someone").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownEndpointIsA401ForAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRequiresAValidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Weak","email":"weak@test.local","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginIsBlockedUntilTheEmailIsVerified() throws Exception {
        register("unverified@test.local");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("unverified@test.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullHappyPath() throws Exception {
        register(EMAIL);

        // The OTP never leaves the server, so read it off the (mocked) email call.
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq(EMAIL), anyString(), codeCaptor.capture(),
                eq(VerificationPurpose.EMAIL_VERIFICATION), anyInt());
        String code = codeCaptor.getValue();
        assertThat(code).matches("\\d{6}");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(EMAIL, code)))
                .andExpect(status().isOk());

        // --- login ---
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        JsonNode tokens = objectMapper.readTree(loginBody).get("data");
        String accessToken = tokens.get("accessToken").asString();
        String refreshToken = tokens.get("refreshToken").asString();

        // --- the access token opens a protected endpoint ---
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // --- refresh rotates the token ---
        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String rotated = objectMapper.readTree(refreshBody).get("data").get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(refreshToken);

        // --- the consumed refresh token must not work a second time ---
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test Customer","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String credentials(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    }
}
