package com.ecomerceshopeonline.ecomercewepapp.security;

import com.ecomerceshopeonline.ecomercewepapp.entity.Role;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the local account on first social sign-in, or links the provider identity onto
 * an existing account with the same email. Shared by the plain OAuth2 path (Facebook) and
 * the OpenID Connect path (Google: the "openid" scope makes Spring use the OIDC user
 * service, which would otherwise skip provisioning entirely).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AccountProvisioner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /** Caller must run inside a transaction (both user services are @Transactional). */
    public User provision(String registrationId, Map<String, Object> attributes, String nameAttributeKey) {
        String email = asString(attributes.get("email"));
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_provided"),
                    "The " + registrationId + " account did not expose an email address");
        }

        AuthProvider provider = providerFor(registrationId);
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        String providerId = asString(attributes.get(nameAttributeKey));
        String picture = extractPicture(registrationId, attributes);

        User user = userRepository.findByEmail(normalisedEmail)
                .map(existing -> linkExisting(existing, providerId, picture,
                        asString(attributes.get("name")), provider))
                .orElseGet(() -> createNew(normalisedEmail, providerId,
                        asString(attributes.get("name")), picture, provider));

        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled"),
                    "This account has been disabled");
        }
        return user;
    }

    public Set<SimpleGrantedAuthority> authoritiesOf(User user) {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet());
    }

    /**
     * Keeps the original provider so a verified password-registered user does not lose
     * password login by signing in with Google once.
     *
     * An UNVERIFIED existing account is different: nobody has proven they own the address,
     * so whoever registered it may be an attacker who pre-registered the victim's email.
     * Its password and self-chosen profile details are therefore discarded before the
     * social identity is attached. The real owner proves the address with the email code
     * (success handler) and can set a password later through forgot-password, which also
     * proves ownership of the inbox.
     */
    private User linkExisting(User existing, String providerId, String picture,
                              String name, AuthProvider provider) {
        boolean changed = false;
        if (!existing.isEmailVerified()) {
            existing.setPassword(null);
            existing.setPhone(null);
            existing.setFullName(name == null || name.isBlank() ? existing.getEmail() : name);
            existing.setProvider(provider);
            if (providerId != null) {
                existing.setProviderId(providerId);
            }
            if (picture != null) {
                existing.setProfileImage(picture);
            }
            log.info("Linked {} identity onto unverified account {}; unproven credentials cleared",
                    provider, existing.getEmail());
            return userRepository.save(existing);
        }
        if (existing.getProviderId() == null && providerId != null) {
            existing.setProviderId(providerId);
            changed = true;
        }
        if (existing.getProfileImage() == null && picture != null) {
            existing.setProfileImage(picture);
            changed = true;
        }
        // Deliberately does NOT mark an unverified account as verified: the shop confirms
        // ownership itself with the 6-digit email code before the first social sign-in.
        return changed ? userRepository.save(existing) : existing;
    }

    private User createNew(String email, String providerId, String name,
                           String picture, AuthProvider provider) {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_CUSTOMER is missing - check that RoleSeeder ran"));

        User user = User.builder()
                .fullName(name == null || name.isBlank() ? email : name)
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .profileImage(picture)
                // Verified with our own email code on the first sign-in (success handler).
                .emailVerified(false)
                .enabled(true)
                .build();
        user.addRole(customerRole);
        log.info("Provisioned new {} account for {}", provider, email);
        return userRepository.save(user);
    }

    private AuthProvider providerFor(String registrationId) {
        return switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "google" -> AuthProvider.GOOGLE;
            case "facebook" -> AuthProvider.FACEBOOK;
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "Unsupported login provider: " + registrationId);
        };
    }

    private String extractPicture(String registrationId, Map<String, Object> attributes) {
        if ("facebook".equalsIgnoreCase(registrationId)) {
            Object pictureObj = attributes.get("picture");
            if (pictureObj instanceof Map<?, ?> pictureMap) {
                Object dataObj = pictureMap.get("data");
                if (dataObj instanceof Map<?, ?> dataMap) {
                    Object url = dataMap.get("url");
                    if (url != null) {
                        return String.valueOf(url);
                    }
                }
            }
        }
        return asString(attributes.get("picture"));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
