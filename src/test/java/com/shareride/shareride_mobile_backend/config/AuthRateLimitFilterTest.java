package com.shareride.shareride_mobile_backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {

    @Test
    void blocksRequestsExceedingWindowLimit() {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        long now = 1_000_000L;

        for (int i = 0; i < 30; i++) {
            assertTrue(filter.allowRequest("127.0.0.1", now + i));
        }

        assertFalse(filter.allowRequest("127.0.0.1", now + 500));
        // After 60 seconds window resets
        assertTrue(filter.allowRequest("127.0.0.1", now + 61_000L));
    }
}
