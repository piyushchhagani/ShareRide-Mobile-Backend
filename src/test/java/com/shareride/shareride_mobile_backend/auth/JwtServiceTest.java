package com.shareride.shareride_mobile_backend.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "shareride-unit-test-secret-minimum-32-characters";

    @Test
    void generatesAndValidatesToken() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("student@campus.edu");

        assertNotNull(token);
        assertEquals("student@campus.edu", jwtService.extractEmail(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("student@campus.edu");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThrows(SecurityException.class, () -> jwtService.extractEmail(tampered));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short"));
    }
}
