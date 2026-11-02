package com.ecomerceshopeonline.ecomercewepapp.security;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.AuthResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.AuthService;
import com.ecomerceshopeonline.ecomercewepapp.service.VerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;

/**
 * Google has authenticated the user. A verified account gets our own token pair by
 * redirecting to the configured front-end callback with the tokens attached. An account
 * whose email is not yet verified (first social sign-in) gets no tokens: a 6-digit code is
 * emailed and the shopper is sent to the SPA's verify page instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final OAuth2AppRedirectResolver appRedirectResolver;
    private final VerificationCodeService verificationCodeService;

    /*
     * Not @Transactional on purpose: issuing the code runs in its own transaction so that a
     * failed email send can be caught here without poisoning an outer transaction.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (response.isCommitted()) {
            log.warn("Response already committed - cannot redirect to the OAuth2 callback");
            return;
        }

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        // Read through the Map: the generic getAttribute() makes String.valueOf pick its
        // char[] overload and fail with a ClassCastException at runtime.
        Object emailAttribute = oAuth2User.getAttributes().get("email");
        String email = emailAttribute == null ? "" : emailAttribute.toString().trim().toLowerCase(Locale.ROOT);

        User user = email.isEmpty() ? null : userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Should not happen now that both OAuth2 and OIDC sign-ins provision the account,
            // but never show the shopper a Whitelabel 500 for it.
            log.error("OAuth2 login succeeded but no local account exists for {}", email);
            redirectWithError(request, response, "We couldn't finish signing you in. Please try again.");
            return;
        }

        if (!user.isEmailVerified()) {
            redirectToEmailVerification(request, response, authentication, user);
            return;
        }

        AuthResponse tokens = authService.issueTokensFor(user);

        String target = UriComponentsBuilder
                .fromUriString(resolveRedirectUri(request))
                .queryParam("accessToken", tokens.accessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .build().toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void redirectToEmailVerification(HttpServletRequest request, HttpServletResponse response,
                                             Authentication authentication, User user) throws IOException {
        boolean sendFailed = false;
        try {
            // Reuses a code sent moments ago instead of failing on the resend cooldown.
            verificationCodeService.issueAndSendUnlessRecent(user, VerificationPurpose.EMAIL_VERIFICATION);
        } catch (RuntimeException ex) {
            log.error("Could not send the email verification code to {} after social sign-in",
                    user.getEmail(), ex);
            sendFailed = true;
        }

        String provider = authentication instanceof OAuth2AuthenticationToken token
                ? token.getAuthorizedClientRegistrationId().toLowerCase(Locale.ROOT)
                : "google";

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(resolveRedirectUri(request))
                .replacePath("/verify-email")
                .replaceQuery(null)
                .fragment(null)
                .queryParam("email", "{email}")
                .queryParam("provider", "{provider}");
        if (sendFailed) {
            builder.queryParam("sendFailed", "1");
        }
        // Template variables are strictly encoded, so a "+" in the address survives as %2B
        // (a literal "+" would be read back as a space by URLSearchParams).
        String target = builder.encode().buildAndExpand(user.getEmail(), provider).toUriString();

        // The OAuth2 login filter has already stored the authentication in the session;
        // drop it so a half-signed-in (unverified) session can never be used.
        clearAuthenticationAttributes(request);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response,
                                   String message) throws IOException {
        String target = UriComponentsBuilder
                .fromUriString(resolveRedirectUri(request))
                .queryParam("error", message)
                .encode()
                .build().toUriString();
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String resolveRedirectUri(HttpServletRequest request) {
        // Recorded when the sign-in started. The Referer is no use here: this request
        // arrives from the provider's page, not from the front-end.
        return appRedirectResolver.redirectUriFor(request);
    }
}
