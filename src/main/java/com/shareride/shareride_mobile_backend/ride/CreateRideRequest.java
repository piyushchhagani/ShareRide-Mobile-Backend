package com.shareride.shareride_mobile_backend.ride;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateRideRequest(
        @NotBlank(message = "Pickup address is required")
        String pickupAddress,

        @NotNull(message = "Pickup latitude is required")
        Double pickupLatitude,

        @NotNull(message = "Pickup longitude is required")
        Double pickupLongitude,

        @NotBlank(message = "Destination address is required")
        String destinationAddress,

        @NotNull(message = "Destination latitude is required")
        Double destinationLatitude,

        @NotNull(message = "Destination longitude is required")
        Double destinationLongitude,

        @NotNull(message = "Departure time is required")
        LocalDateTime departureTime,

        @NotNull(message = "Available seats is required")
        @Min(value = 1, message = "Available seats must be at least 1")
        Integer availableSeats,

        String vehicleType,
        String vehicleNumber
) {}