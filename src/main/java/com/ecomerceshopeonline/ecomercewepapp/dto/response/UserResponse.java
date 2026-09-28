package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/** Safe projection of {@link User} - deliberately never carries the password hash. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String profileImage,
        AuthProvider provider,
        boolean emailVerified,
        boolean enabled,
        Set<RoleName> roles,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfileImage(),
                user.getProvider(),
                user.isEmailVerified(),
                user.isEnabled(),
                user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet()),
                user.getCreatedAt()
        );
    }
}
