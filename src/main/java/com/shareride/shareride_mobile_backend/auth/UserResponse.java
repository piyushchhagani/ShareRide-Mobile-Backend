package com.shareride.shareride_mobile_backend.auth;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role
) {}
