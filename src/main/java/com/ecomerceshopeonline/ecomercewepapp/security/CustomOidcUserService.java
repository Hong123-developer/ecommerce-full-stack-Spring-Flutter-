package com.ecomerceshopeonline.ecomercewepapp.security;

import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Google sign-in. Because the registration asks for the "openid" scope, Spring Security
 * loads the user through the OpenID Connect user service, not {@link CustomOAuth2UserService}.
 * Without this class no local account was ever created for Google users, and the success
 * handler failed with a 500.
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private static final String DEFAULT_NAME_ATTRIBUTE = "sub";

    private final OAuth2AccountProvisioner provisioner;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String configured = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        String nameAttributeKey = StringUtils.hasText(configured) ? configured : DEFAULT_NAME_ATTRIBUTE;

        User user = provisioner.provision(registrationId, oidcUser.getAttributes(), nameAttributeKey);
        return new DefaultOidcUser(provisioner.authoritiesOf(user), oidcUser.getIdToken(),
                oidcUser.getUserInfo(), nameAttributeKey);
    }
}
