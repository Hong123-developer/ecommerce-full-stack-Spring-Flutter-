package com.ecomerceshopeonline.ecomercewepapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/** Sends the user back to the front-end with an error rather than to a Spring error page. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final OAuth2AppRedirectResolver appRedirectResolver;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google sign-in failed: {}", exception.getMessage());

        String target = UriComponentsBuilder
                .fromUriString(appRedirectResolver.redirectUriFor(request))
                .queryParam("error", exception.getMessage())
                .encode()
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
