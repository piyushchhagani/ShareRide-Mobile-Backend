package com.shareride.shareride_mobile_backend.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRatingRequest(
        @NotNull(message = "Ride ID is required")
        Long rideId,

        @NotNull(message = "Rated user ID is required")
        Long ratedUserId,

        @NotNull(message = "Score is required")
        @Min(value = 1, message = "Rating score must be between 1 and 5")
        @Max(value = 5, message = "Rating score must be between 1 and 5")
        Integer score,

        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment
) {}
