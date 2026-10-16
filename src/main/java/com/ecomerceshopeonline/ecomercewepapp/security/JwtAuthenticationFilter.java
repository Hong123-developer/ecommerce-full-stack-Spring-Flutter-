package com.ecomerceshopeonline.ecomercewepapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <token>} and populates the SecurityContext.
 * <p>
 * The user is reloaded from the database on every request rather than trusted from the
 * token claims, so disabling an account or changing its roles takes effect immediately
 * instead of waiting for the access token to expire.
 * <p>
 * A token only counts for the account it was issued to - the same id <em>and</em> the same
 * email. After a backup is restored, an id may belong to a different person than when the
 * token was signed; such a token leaves the request anonymous instead of acting as them.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.extractIdentity(token).ifPresent(identity -> authenticate(identity, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(JwtService.TokenIdentity identity, HttpServletRequest request) {
        try {
            CustomUserDetails principal = (CustomUserDetails) userDetailsService.loadUserById(identity.userId());
            if (!principal.isEnabled()) {
                return;
            }
            if (identity.email() == null || !identity.email().equalsIgnoreCase(principal.getUsername())) {
                // The id now belongs to a different account than the one this token was issued to.
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            // Token references an account that no longer exists - stay anonymous.
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
