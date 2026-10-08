package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomOidcUserService;
import com.ecomerceshopeonline.ecomercewepapp.security.OAuth2LoginSuccessHandler;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Google sign-in asks for the "openid" scope, so Spring loads the user through the OIDC
 * user service. These tests drive that path directly (no network: without a user-info
 * URI the OIDC service builds the user from the ID token) and check the local account.
 * A first social sign-in must confirm the address with the shop's own 6-digit email code
 * before any JWT is issued; once verified, later sign-ins go straight in.
 */
@SpringBootTest
class GoogleOidcLoginIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private CustomOidcUserService oidcUserService;
    @Autowired private OAuth2LoginSuccessHandler successHandler;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private WebApplicationContext context;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        dataSeeder.run(null);
    }

    @Test
    void firstGoogleSignInCreatesAnUnverifiedCustomerAccount() {
        String email = "Google.New" + SEQ.incrementAndGet() + "@Example.test";

        OidcUser principal = oidcUserService.loadUser(request(email, "Dara Sok", "sub-" + SEQ.get()));

        User saved = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getProviderId()).isEqualTo("sub-" + SEQ.get());
        assertThat(saved.getFullName()).isEqualTo("Dara Sok");
        // The shop confirms the address itself with the 6-digit code on the first sign-in.
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(RoleName.ROLE_CUSTOMER.name());
        // The success handler looks the account up by this attribute.
        assertThat((String) principal.getAttribute("email")).isEqualTo(email);

        // Signing in again reuses the same account.
        oidcUserService.loadUser(request(email, "Dara Sok", "sub-" + SEQ.get()));
        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))).hasSize(1);
    }

    @Test
    void unverifiedGoogleUserMustConfirmTheEmailCodeBeforeGettingTokens() throws Exception {
        String email = "handler+" + SEQ.incrementAndGet() + "@example.test";
        OidcUser principal = oidcUserService.loadUser(request(email, "Handler", "sub-h-" + SEQ.get()));
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        // First sign-in: no tokens, a code is emailed, and the SPA verify page is the target.
        MockHttpServletResponse first = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), first, authentication);

        assertThat(first.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/verify-email?email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&provider=google")
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq(email), anyString(), codeCaptor.capture(),
                eq(VerificationPurpose.EMAIL_VERIFICATION), anyInt());
        String code = codeCaptor.getValue();
        assertThat(code).matches("\\d{6}");

        // Pressing "Continue with Google" again straight away must not fail on the resend
        // cooldown: the code already sent is reused and nothing new is emailed.
        MockHttpServletResponse again = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), again, authentication);
        assertThat(again.getRedirectedUrl()).contains("/verify-email?").doesNotContain("sendFailed")
                .doesNotContain("accessToken");
        verify(emailService, times(1)).sendVerificationCode(eq(email), anyString(), anyString(),
                eq(VerificationPurpose.EMAIL_VERIFICATION), anyInt());

        mockMvc().perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();

        // Every later Google sign-in goes straight in.
        OidcUser later = oidcUserService.loadUser(request(email, "Handler", "sub-h-" + SEQ.get()));
        MockHttpServletResponse second = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), second,
                new OAuth2AuthenticationToken(later, later.getAuthorities(), "google"));

        assertThat(second.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth2/callback")
                .contains("accessToken=")
                .contains("refreshToken=")
                .doesNotContain("error=");
    }

    @Test
    void failedCodeEmailStillLandsOnTheVerifyPageWithoutTokens() throws Exception {
        String email = "sendfail" + SEQ.incrementAndGet() + "@example.test";
        OidcUser principal = oidcUserService.loadUser(request(email, "Fail", "sub-f-" + SEQ.get()));
        doThrow(new RuntimeException("SMTP down")).when(emailService).sendVerificationCode(
                eq(email), anyString(), anyString(), any(), anyInt());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/verify-email?")
                .contains("provider=google")
                .contains("sendFailed=1")
                .doesNotContain("accessToken");
        // The half-signed-in OAuth2 session is thrown away.
        assertThat(request.getSession(false)).isNull();
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void alreadyVerifiedAccountsStayVerifiedAndGoStraightIn() throws Exception {
        String email = "verified" + SEQ.incrementAndGet() + "@example.test";
        User local = User.builder()
                .fullName("Verified Person")
                .email(email)
                .password(passwordEncoder.encode("Secret@123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        local.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        userRepository.save(local);

        OidcUser principal = oidcUserService.loadUser(request(email, "Google Name", "sub-v-" + SEQ.get()));
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth2/callback")
                .contains("accessToken=");
        verify(emailService, never()).sendVerificationCode(eq(email), anyString(), anyString(),
                any(), anyInt());
    }

    @Test
    void googleSignInLinksAnUnverifiedPasswordAccountWithoutVerifyingIt() {
        String email = "linked" + SEQ.incrementAndGet() + "@example.test";
        User local = User.builder()
                .fullName("Local Person")
                .email(email)
                .phone("012345678")
                .password(passwordEncoder.encode("Secret@123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .enabled(true)
                .build();
        local.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        userRepository.save(local);

        oidcUserService.loadUser(request(email, "Google Name", "sub-link-" + SEQ.get()));

        User after = userRepository.findByEmail(email).orElseThrow();
        assertThat(after.getProviderId()).isEqualTo("sub-link-" + SEQ.get());
        assertThat(after.isEmailVerified()).isFalse();
        // Nobody proved ownership of the unverified account, so its credentials and
        // self-chosen details are dropped in favour of the Google identity.
        assertThat(after.getPassword()).isNull();
        assertThat(after.getPhone()).isNull();
        assertThat(after.getFullName()).isEqualTo("Google Name");
        assertThat(after.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    void preRegisteredPasswordStopsWorkingAfterVictimLinksGoogleAndVerifies() throws Exception {
        String email = "takeover" + SEQ.incrementAndGet() + "@example.test";
        // 1) An attacker registers the victim's address with a password they know.
        mockMvc().perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Attacker","email":"%s","password":"Attack@123","phone":"099999999"}
                                """.formatted(email)))
                .andExpect(status().is2xxSuccessful());
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();

        // 2) The real owner continues with Google and receives the code.
        OidcUser principal = oidcUserService.loadUser(request(email, "Real Owner", "sub-t-" + SEQ.get()));
        MockHttpServletResponse first = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), first,
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        assertThat(first.getRedirectedUrl()).contains("/verify-email?");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce()).sendVerificationCode(eq(email),
                anyString(), codeCaptor.capture(), eq(VerificationPurpose.EMAIL_VERIFICATION), anyInt());
        String code = codeCaptor.getValue();

        // 3) The owner enters the code.
        mockMvc().perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)))
                .andExpect(status().isOk());
        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getFullName()).isEqualTo("Real Owner");
        assertThat(verified.getPhone()).isNull();

        // 4) The attacker's password no longer opens the account.
        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Attack@123\"}".formatted(email)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void disabledAccountIsRejected() {
        String email = "disabled" + SEQ.incrementAndGet() + "@example.test";
        oidcUserService.loadUser(request(email, "Off", "sub-off-" + SEQ.get()));
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        assertThatThrownBy(() -> oidcUserService.loadUser(request(email, "Off", "sub-off-" + SEQ.get())))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("disabled");
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static OidcUserRequest request(String email, String name, String sub) {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token", now, now.plusSeconds(3600), Set.of("openid", "profile", "email"));
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(3600), Map.of(
                "iss", "https://accounts.google.com",
                "sub", sub,
                "aud", "test-client",
                "email", email,
                "email_verified", true,
                "name", name,
                "picture", "https://lh3.googleusercontent.com/a/test"));
        return new OidcUserRequest(google, accessToken, idToken);
    }
}
