package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.*;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AuthResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.UserResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;

public interface AuthService {

    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void verifyEmail(VerifyCodeRequest request);

    void resendVerificationCode(EmailOnlyRequest request);

    void forgotPassword(EmailOnlyRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void logoutEverywhere(Long userId);

    UserResponse currentUser(Long userId);

    /** Mints a token pair for an already-authenticated user. Used by the Google login handler. */
    AuthResponse issueTokensFor(User user);
}
