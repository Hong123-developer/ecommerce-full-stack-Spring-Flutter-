package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer statistics must describe shoppers only - administrators registering (or
 * being seeded) must not show up as "new customers".
 */
@SpringBootTest
class AdminCustomerIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
    }

    @Test
    void signupStatisticsCountCustomersButNotAdministrators() throws Exception {
        save("customer", RoleName.ROLE_CUSTOMER);
        save("staff", RoleName.ROLE_ADMIN);

        long customers = json("/api/v1/admin/customers?size=1").path("data").path("totalElements").asLong();

        // Every account in the test database is younger than a year.
        long signups = 0;
        for (JsonNode day : json("/api/v1/admin/customers/registrations?days=365").path("data")) {
            signups += day.path("count").asLong();
        }
        assertThat(signups).isEqualTo(customers);

        JsonNode dashboard = json("/api/v1/admin/dashboard?days=365").path("data").path("customers");
        assertThat(dashboard.path("newLast30Days").asLong()).isEqualTo(customers);
        assertThat(dashboard.path("total").asLong()).isEqualTo(customers);
    }

    private JsonNode json(String url) throws Exception {
        String body = mockMvc.perform(get(url).with(asAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void save(String prefix, RoleName role) {
        int n = SEQ.incrementAndGet();
        User account = User.builder()
                .fullName(prefix + " " + n)
                .email(prefix + n + "@signups.test")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        account.addRole(roleRepository.findByName(role).orElseThrow());
        userRepository.save(account);
    }
}
