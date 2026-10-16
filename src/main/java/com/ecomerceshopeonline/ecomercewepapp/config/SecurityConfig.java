package com.ecomerceshopeonline.ecomercewepapp.config;

import com.ecomerceshopeonline.ecomercewepapp.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CustomOAuth2UserService oAuth2UserService;
    private final CustomOidcUserService oidcUserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final AppProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // No cookies are used for API auth, so there is no CSRF vector to protect.
                .csrf(AbstractHttpConfigurer::disable)
                // IF_REQUIRED rather than STATELESS: the OAuth2 authorization-code flow has to
                // park the pending authorization request somewhere between the two redirects.
                // Normal API calls never touch the session - they authenticate per-request via JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // --- public ---
                        // Listed one by one on purpose. A blanket "/api/v1/auth/**" would also
                        // expose /me, /change-password and /logout-all, whose handlers assume an
                        // authenticated principal and would throw NPE -> 500 instead of 401.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-code",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        // /error must stay open or every failure turns into a confusing 401/redirect
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/", "/index.html", "/*.html", "/favicon.ico").permitAll()
                        // browsing the catalogue does not require an account
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // "may I review this?" is about the signed-in shopper - must precede the rule below
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/reviews/me").authenticated()
                        // includes a product's reviews and their summary
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // the home-page hero carousel is part of the public storefront too
                        .requestMatchers(HttpMethod.GET, "/api/v1/hero-slides/**").permitAll()
                        // store name, logo and contact details shown in the storefront header/footer
                        .requestMatchers(HttpMethod.GET, "/api/v1/store-settings").permitAll()
                        // delivery companies / store pickup and their fees, shown in the cart and checkout
                        .requestMatchers(HttpMethod.GET, "/api/v1/delivery-options").permitAll()
                        // uploaded product images are public, like the catalogue that shows them
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // which payment methods checkout may offer (card only once configured)
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/methods").permitAll()
                        // the card provider's server-to-server pushback; it only triggers a re-check
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/payway/callback").permitAll()
                        // --- role gated ---
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // --- everything else needs a signed-in user ---
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)        // Facebook (plain OAuth2)
                                .oidcUserService(oidcUserService))     // Google ("openid" scope = OIDC)
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit origins, not "*" - a wildcard is rejected when credentials are allowed.
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
