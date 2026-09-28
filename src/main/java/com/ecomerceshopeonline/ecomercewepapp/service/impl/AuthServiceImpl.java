package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.*;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.AuthResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.UserResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.RefreshToken;
import com.ecomerceshopeonline.ecomercewepapp.entity.Role;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.VerificationPurpose;
import com.ecomerceshopeonline.ecomercewepapp.exception.ApiException;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.security.JwtService;
import com.ecomerceshopeonline.ecomercewepapp.service.AuthService;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import com.ecomerceshopeonline.ecomercewepapp.service.RefreshTokenService;
import com.ecomerceshopeonline.ecomercewepapp.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_CUSTOMER is missing - check that RoleSeeder ran"));

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .phone(blankToNull(request.phone()))
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .enabled(true)
                .build();
        user.addRole(customerRole);
        userRepository.save(user);

        verificationCodeService.issueAndSend(user, VerificationPurpose.EMAIL_VERIFICATION);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        // Throws BadCredentialsException / DisabledException - both mapped in GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!user.isEmailVerified()) {
            throw new ApiException(
                    "Please verify your email address before signing in", HttpStatus.FORBIDDEN);
        }
        return issueTokensFor(user);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyCodeRequest request) {
        User user = requireUser(request.email());
        if (user.isEmailVerified()) {
            throw new BadRequestException("This email is already verified");
        }

        verificationCodeService.consume(user, VerificationPurpose.EMAIL_VERIFICATION, request.code());
        user.setEmailVerified(true);
        userRepository.save(user);

        emailService.sendWelcome(user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional
    public void resendVerificationCode(EmailOnlyRequest request) {
        User user = requireUser(request.email());
        if (user.isEmailVerified()) {
            throw new BadRequestException("This email is already verified");
        }
        verificationCodeService.issueAndSend(user, VerificationPurpose.EMAIL_VERIFICATION);
    }

    @Override
    @Transactional
    public void forgotPassword(EmailOnlyRequest request) {
        // Deliberately silent when the address is unknown: a different response here would
        // let anyone test which emails have accounts.
        userRepository.findByEmail(normalizeEmail(request.email())).ifPresent(user ->
                verificationCodeService.issueAndSend(user, VerificationPurpose.PASSWORD_RESET));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = requireUser(request.email());
        verificationCodeService.consume(user, VerificationPurpose.PASSWORD_RESET, request.code());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // A reset usually means the old password leaked - drop every existing session.
        user.setEmailVerified(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BadRequestException(
                    "This account signs in with Google. Use 'forgot password' to set a password first.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenService.validate(request.refreshToken());
        User user = token.getUser();

        if (!user.isEnabled()) {
            throw new ApiException("This account has been disabled", HttpStatus.FORBIDDEN);
        }

        // Rotation: the presented token is burned so a stolen copy cannot be replayed.
        refreshTokenService.revoke(token);
        return issueTokensFor(user);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(refreshTokenService.validate(request.refreshToken()));
    }

    @Override
    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    @Override
    @Transactional
    public AuthResponse issueTokensFor(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user);
        return AuthResponse.of(accessToken, refreshToken,
                jwtService.getAccessTokenExpirationMillis(), UserResponse.from(user));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("No account for that email"));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
