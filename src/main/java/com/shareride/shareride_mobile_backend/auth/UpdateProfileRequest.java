package com.shareride.shareride_mobile_backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Size(max = 20, message = "Phone number must be at most 20 characters")
        String phone,

        @Size(max = 500, message = "Avatar URL must be at most 500 characters")
        String avatarUrl
) {}
