package com.ecomerceshopeonline.ecomercewepapp.security;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

/**
 * Remembers which front-end started a social sign-in, so the success / failure handlers can
 * send the tokens back to it rather than always to {@code app.oauth2.redirect-uri}.
 * <p>
 * The front-end may pass {@code ?app_redirect_uri=<origin>/oauth2/callback} to
 * {@code /oauth2/authorization/{provider}}; otherwise the Referer of that first request is used.
 * Either is checked against an allow-list (tokens ride on this redirect, so it must never be
 * an open redirect) and parked in the session that already holds the pending authorization
 * request. The Referer can't be read later: by the time the handlers run, the browser is
 * coming from the provider's page.
 */
@Component
public class OAuth2AppRedirectResolver implements OAuth2AuthorizationRequestResolver {

    public static final String PARAM = "app_redirect_uri";
    private static final String SESSION_ATTRIBUTE = OAuth2AppRedirectResolver.class.getName() + ".REDIRECT_URI";
    private static final String CALLBACK_PATH = "/oauth2/callback";

    private final OAuth2AuthorizationRequestResolver delegate;
    private final AppProperties properties;

    public OAuth2AppRedirectResolver(ClientRegistrationRepository clientRegistrationRepository,
                                     AppProperties properties) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.properties = properties;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    /** The front-end callback for the sign-in in progress, or the configured default. */
    public String redirectUriFor(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object stored = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
        return stored != null ? stored.toString() : properties.oauth2().redirectUri();
    }

    private OAuth2AuthorizationRequest remember(HttpServletRequest request,
                                                OAuth2AuthorizationRequest authorizationRequest) {
        // Null means this request isn't the start of a sign-in.
        if (authorizationRequest == null) {
            return null;
        }
        String target = requestedRedirectUri(request);
        HttpSession session = request.getSession();
        if (target != null) {
            session.setAttribute(SESSION_ATTRIBUTE, target);
        } else {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
        return authorizationRequest;
    }

    private String requestedRedirectUri(HttpServletRequest request) {
        String explicit = request.getParameter(PARAM);
        if (explicit != null && isAllowed(explicit)) {
            return explicit;
        }
        // A local dev front-end on any port (e.g. Flutter web), identified by the page it came from
        URI referer = parse(request.getHeader("Referer"));
        if (referer != null && isLoopback(referer)) {
            return referer.getScheme() + "://" + referer.getRawAuthority() + CALLBACK_PATH;
        }
        return null;
    }

    private boolean isAllowed(String candidate) {
        URI uri = parse(candidate);
        if (uri == null || !CALLBACK_PATH.equals(uri.getPath())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        URI configured = parse(properties.oauth2().redirectUri());
        boolean sameOriginAsConfigured = configured != null
                && Objects.equals(uri.getScheme(), configured.getScheme())
                && Objects.equals(uri.getRawAuthority(), configured.getRawAuthority());
        return sameOriginAsConfigured || isLoopback(uri);
    }

    private static boolean isLoopback(URI uri) {
        return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
