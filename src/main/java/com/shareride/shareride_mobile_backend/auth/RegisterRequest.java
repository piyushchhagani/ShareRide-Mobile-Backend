package com.shareride.shareride_mobile_backend.auth;

public record RegisterRequest(
        String name,
        String email,
        String password,
        String phone
) {}