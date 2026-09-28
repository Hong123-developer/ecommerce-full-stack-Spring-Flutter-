package com.ecomerceshopeonline.ecomercewepapp.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(String accessToken, String refreshToken,
                                  long expiresInMillis, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMillis / 1000, user);
    }
}
