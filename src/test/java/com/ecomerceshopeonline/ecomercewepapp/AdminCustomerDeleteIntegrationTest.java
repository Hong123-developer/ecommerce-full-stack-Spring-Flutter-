package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.RefreshToken;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.VerificationCode;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.repository.AddressRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartItemRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.CartRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.OrderRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RefreshTokenRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.VerificationCodeRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.security.JwtService;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin deletion of customer accounts: only shoppers without orders can be removed, and
 * removal takes every row that points at the account with it.
 */
@SpringBootTest
class AdminCustomerDeleteIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String URL = "/api/v1/admin/customers/";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AddressRepository addressRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private VerificationCodeRepository verificationCodeRepository;
    @Autowired private JwtService jwtService;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
    }

    @Test
    void deletingACustomerRemovesEverythingTheyOwnAndFreesTheEmail() throws Exception {
        User customer = save("gone", RoleName.ROLE_CUSTOMER);
        Long id = customer.getId();
        RequestPostProcessor asCustomer = user(new CustomUserDetails(customer));

        long productId = createProduct();
        createAddress(asCustomer);
        addToCart(asCustomer, productId, 2);
        Long cartId = cartRepository.findByUserId(id).orElseThrow().getId();
        assertThat(cartItemRepository.findByCartId(cartId)).hasSize(1);

        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .user(customer)
                .tokenHash("hash-" + SEQ.incrementAndGet())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
        VerificationCode code = verificationCodeRepository.save(VerificationCode.builder()
                .user(customer)
                .codeHash("code-" + SEQ.incrementAndGet())
                .purpose(VerificationPurpose.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());
        String accessToken = jwtService.generateAccessToken(new CustomUserDetails(customer));

        mockMvc.perform(delete(URL + id).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Customer deleted"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(addressRepository.findByUserId(id)).isEmpty();
        assertThat(cartRepository.findById(cartId)).isEmpty();
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
        assertThat(refreshTokenRepository.findById(token.getId())).isEmpty();
        assertThat(verificationCodeRepository.findById(code.getId())).isEmpty();

        // Their still-unexpired access token no longer opens anything.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // A second delete finds nothing.
        mockMvc.perform(delete(URL + id).with(asAdmin))
                .andExpect(status().isNotFound());

        // The email is free to register again.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Back Again","email":"%s","password":"Secret123"}
                                """.formatted(customer.getEmail())))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail(customer.getEmail())).isPresent();
    }

    @Test
    void customerWithOrdersCannotBeDeleted() throws Exception {
        User customer = save("buyer", RoleName.ROLE_CUSTOMER);
        RequestPostProcessor asCustomer = user(new CustomUserDetails(customer));
        long productId = createProduct();
        long addressId = createAddress(asCustomer);
        addToCart(asCustomer, productId, 1);
        checkout(asCustomer, addressId);

        mockMvc.perform(delete(URL + customer.getId()).with(asAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This customer has 1 order(s). Disable the account instead to keep your sales history."));

        assertThat(userRepository.findById(customer.getId())).isPresent();
        assertThat(addressRepository.findByUserId(customer.getId())).hasSize(1);
        assertThat(orderRepository.countByUserId(customer.getId())).isEqualTo(1);
    }

    @Test
    void administratorsCannotBeDeleted() throws Exception {
        User staff = save("staff", RoleName.ROLE_ADMIN);

        mockMvc.perform(delete(URL + staff.getId()).with(asAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Administrator accounts can't be deleted here"));
        assertThat(userRepository.findById(staff.getId())).isPresent();
    }

    @Test
    void adminCannotDeleteThemselves() throws Exception {
        User me = save("me", RoleName.ROLE_ADMIN);

        mockMvc.perform(delete(URL + me.getId()).with(user(new CustomUserDetails(me))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't delete your own account"));
        assertThat(userRepository.findById(me.getId())).isPresent();
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        mockMvc.perform(delete(URL + 987_654_321L).with(asAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminsCannotDelete() throws Exception {
        User victim = save("victim", RoleName.ROLE_CUSTOMER);
        User caller = save("caller", RoleName.ROLE_CUSTOMER);

        mockMvc.perform(delete(URL + victim.getId()).with(user(new CustomUserDetails(caller))))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(victim.getId())).isPresent();
    }

    @Test
    void customerListIncludesOrderCount() throws Exception {
        User buyer = save("listbuyer", RoleName.ROLE_CUSTOMER);
        User browser = save("listbrowser", RoleName.ROLE_CUSTOMER);
        RequestPostProcessor asBuyer = user(new CustomUserDetails(buyer));
        long productId = createProduct();
        long addressId = createAddress(asBuyer);
        addToCart(asBuyer, productId, 1);
        checkout(asBuyer, addressId);

        assertThat(listedOrderCount(buyer.getEmail())).isEqualTo(1);
        assertThat(listedOrderCount(browser.getEmail())).isEqualTo(0);
    }

    // ------------------------------------------------------------------ helpers

    private long listedOrderCount(String email) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/customers").param("search", email).with(asAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).has("orderCount")).isTrue();
        return content.get(0).path("orderCount").asLong();
    }

    private User save(String prefix, RoleName role) {
        int n = SEQ.incrementAndGet();
        User account = User.builder()
                .fullName(prefix + " " + n)
                .email(prefix + n + "@delete.test")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        account.addRole(roleRepository.findByName(role).orElseThrow());
        return userRepository.save(account);
    }

    private long createProduct() throws Exception {
        String category = mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete-%d\"}".formatted(SEQ.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(category).get("data").get("id").asLong();

        String product = mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"Mug","sku":"SKU-DEL-%d","price":5.00,"stockQuantity":10}
                                """.formatted(categoryId, SEQ.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(product).get("data").get("id").asLong();
    }

    private long createAddress(RequestPostProcessor as) throws Exception {
        String body = mockMvc.perform(post("/api/v1/addresses").with(as)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"Sok Dara","phone":"012345678",
                                 "province":"Phnom Penh","district":"Chamkarmon",
                                 "addressDetail":"No. 12, St. 271"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private void addToCart(RequestPostProcessor as, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").with(as)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private void checkout(RequestPostProcessor as, long addressId) throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout").with(as)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"CASH_ON_DELIVERY\"}".formatted(addressId)))
                .andExpect(status().isCreated());
    }
}
