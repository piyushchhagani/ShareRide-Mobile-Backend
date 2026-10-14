package com.shareride.shareride_mobile_backend.ride;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record FindRideRequest(
        @NotNull(message = "Pickup latitude is required")
        Double pickupLatitude,

        @NotNull(message = "Pickup longitude is required")
        Double pickupLongitude,

        @NotNull(message = "Destination latitude is required")
        Double destinationLatitude,

        @NotNull(message = "Destination longitude is required")
        Double destinationLongitude,

        @NotNull(message = "Departure time is required")
        LocalDateTime departureTime,

        @NotNull(message = "Seats count is required")
        @Min(value = 1, message = "Seats must be at least 1")
        Integer seats
) {}