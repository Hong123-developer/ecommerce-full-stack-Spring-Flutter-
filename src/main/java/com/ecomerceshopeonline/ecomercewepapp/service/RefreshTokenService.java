package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.entity.RefreshToken;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;

public interface RefreshTokenService {

    /** Creates a new token row and returns the raw value - only the hash is persisted. */
    String issue(User user);

    /** Resolves a raw token to its live row, or throws if it is unknown, revoked, or expired. */
    RefreshToken validate(String rawToken);

    void revoke(RefreshToken token);

    void revokeAllForUser(Long userId);
}
