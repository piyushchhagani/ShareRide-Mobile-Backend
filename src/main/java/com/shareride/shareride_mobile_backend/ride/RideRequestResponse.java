package com.shareride.shareride_mobile_backend.ride;

import java.time.LocalDateTime;

public record RideRequestResponse(
        Long id,
        Long rideId,
        Long passengerId,
        String passengerName,
        String status,
        Integer requestedSeats,
        LocalDateTime createdAt
) {
}