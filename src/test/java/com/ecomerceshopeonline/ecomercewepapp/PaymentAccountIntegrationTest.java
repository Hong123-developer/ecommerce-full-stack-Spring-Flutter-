package com.ecomerceshopeonline.ecomercewepapp;

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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The bank account shown on the payment page: admins edit it, signed-in shoppers read it. */
@SpringBootTest
class PaymentAccountIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/payment-account";
    private static final String PUBLIC = "/api/v1/payment-account";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailService emailService;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");
    private final RequestPostProcessor asShopper = user("shopper").roles("CUSTOMER");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminsSaveItAndShoppersSeeIt() throws Exception {
        mockMvc.perform(put(ADMIN).with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(account("  Test Account Holder  ", "0001-00000000-01", "0001-00000000-02"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").value("Test Account Holder"))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mockMvc.perform(get(PUBLIC).with(asShopper))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("ACLEDA Bank"))
                .andExpect(jsonPath("$.data.accountName").value("Test Account Holder"))
                .andExpect(jsonPath("$.data.usdAccountNumber").value("0001-00000000-01"))
                .andExpect(jsonPath("$.data.khrAccountNumber").value("0001-00000000-02"));

        // A blank field is cleared.
        mockMvc.perform(put(ADMIN).with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(account("Test Account Holder", "0001-00000000-01", " "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.khrAccountNumber").doesNotExist());
    }

    @Test
    void onlyAdminsChangeItAndOnlySignedInUsersReadIt() throws Exception {
        mockMvc.perform(get(PUBLIC)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(ADMIN).with(asShopper).contentType(MediaType.APPLICATION_JSON)
                        .content(json(account("Mallory", "1111-11111111-11", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountNumbersAreChecked() throws Exception {
        mockMvc.perform(put(ADMIN).with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(account("Test Account Holder", "not-a-number", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.usdAccountNumber").value("Use 6-40 digits, spaces or dashes"));
    }

    private static Map<String, Object> account(String name, String usd, String khr) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bankName", "ACLEDA Bank");
        body.put("accountName", name);
        body.put("usdAccountNumber", usd);
        body.put("khrAccountNumber", khr);
        return body;
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }
}
