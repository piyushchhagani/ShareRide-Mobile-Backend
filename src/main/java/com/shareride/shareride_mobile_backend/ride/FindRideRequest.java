package com.shareride.shareride_mobile_backend.ride;

import java.time.LocalDateTime;

public record FindRideRequest(
        Double pickupLatitude,
        Double pickupLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        LocalDateTime departureTime,
        Integer seats
) {}