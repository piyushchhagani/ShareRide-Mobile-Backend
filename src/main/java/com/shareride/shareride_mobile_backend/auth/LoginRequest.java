package com.shareride.shareride_mobile_backend.auth;

public record LoginRequest(
        String email,
        String password
) {}