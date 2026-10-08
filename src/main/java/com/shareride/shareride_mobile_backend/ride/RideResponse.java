package com.shareride.shareride_mobile_backend.ride;

import java.time.LocalDateTime;

public record RideResponse(
        Long id,
        Long driverId,
        String driverName,

        String pickupAddress,
        double pickupLatitude,
        double pickupLongitude,

        String destinationAddress,
        double destinationLatitude,
        double destinationLongitude,

        LocalDateTime departureTime,

        Integer availableSeats,

        String vehicleType,
        String vehicleNumber,

        String status,

        Double matchScore,
        Double routeScore,
        Double timeScore,
        Double pickupScore,
        Double destinationScore,
        Double detourScore
) {
}