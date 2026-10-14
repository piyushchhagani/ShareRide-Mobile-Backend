package com.shareride.shareride_mobile_backend.ride;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.NonNull;

public record CreateRideRequestDto(
        @NotNull(message = "Ride ID is required")
        @NonNull
        Long rideId,

        @Min(value = 1, message = "Requested seats must be at least 1")
        Integer seats
) {}