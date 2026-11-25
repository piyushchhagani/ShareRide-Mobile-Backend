package com.shareride.shareride_mobile_backend.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        Long rideId,
        boolean read,
        LocalDateTime createdAt
) {}
