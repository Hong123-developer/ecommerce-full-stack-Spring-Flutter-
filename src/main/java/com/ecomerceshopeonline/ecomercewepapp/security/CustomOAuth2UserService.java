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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs after Google hands back the user profile. Creates the local account on first
 * sign-in, or links the Google identity onto an existing account with the same email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = asString(attributes.get("email"));
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_provided"),
                    "The " + registrationId + " account did not expose an email address");
        }

        User user = provision(
                email.toLowerCase(Locale.ROOT),
                asString(attributes.get(nameAttributeKey)),
                asString(attributes.get("name")),
                extractPicture(registrationId, attributes),
                providerFor(registrationId));

        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled"),
                    "This account has been disabled");
        }

        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet());

        return new DefaultOAuth2User(authorities, attributes, nameAttributeKey);
    }

    private User provision(String email, String providerId, String name,
                           String picture, AuthProvider provider) {
        return userRepository.findByEmail(email)
                .map(existing -> linkExisting(existing, providerId, picture))
                .orElseGet(() -> createNew(email, providerId, name, picture, provider));
    }

    /**
     * Keeps the original provider so a password-registered user does not lose password
     * login by signing in with Google once.
     */
    private User linkExisting(User existing, String providerId, String picture) {
        boolean changed = false;
        if (existing.getProviderId() == null && providerId != null) {
            existing.setProviderId(providerId);
            changed = true;
        }
        if (existing.getProfileImage() == null && picture != null) {
            existing.setProfileImage(picture);
            changed = true;
        }
        if (!existing.isEmailVerified()) {
            // Google already proved ownership of this address.
            existing.setEmailVerified(true);
            changed = true;
        }
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
                .emailVerified(true)
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

