package com.shareride.shareride_mobile_backend.rating;

import java.time.LocalDateTime;

public record RatingResponse(
        Long id,
        Long rideId,
        Long raterId,
        String raterName,
        Long ratedUserId,
        String ratedUserName,
        Integer score,
        String comment,
        LocalDateTime createdAt
) {}
