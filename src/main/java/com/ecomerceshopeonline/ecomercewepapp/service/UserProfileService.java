package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateProfileRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.UserResponse;

public interface UserProfileService {

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
}
