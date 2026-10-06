package com.shareride.shareride_mobile_backend.auth;

public record LoginResponse(
        String token,
        Long id,
        String name,
        String email,
        String role
) {
    public LoginResponse(
            Long id,
            String name,
            String email,
            String role
    ) {
        this(null, id, name, email, role);
    }
}