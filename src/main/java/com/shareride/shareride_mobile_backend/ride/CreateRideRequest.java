package com.shareride.shareride_mobile_backend.ride;

import java.time.LocalDateTime;

public record CreateRideRequest(
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        String destinationAddress,
        Double destinationLatitude,
        Double destinationLongitude,
        LocalDateTime departureTime,
        Integer availableSeats,
        String vehicleType,
        String vehicleNumber
) {}