package com.shareride.shareride_mobile_backend.rating;

import java.util.List;

public record UserRatingSummaryResponse(
        Long userId,
        Double averageRating,
        long totalRatings,
        long totalCompletedRides,
        List<RatingResponse> ratings
) {}
