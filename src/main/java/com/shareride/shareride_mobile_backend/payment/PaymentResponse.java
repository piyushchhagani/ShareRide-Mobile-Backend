package com.shareride.shareride_mobile_backend.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long rideRequestId,
        Long rideId,
        String pickupAddress,
        String destinationAddress,
        Long passengerId,
        String passengerName,
        Long driverId,
        String driverName,
        Integer seats,
        BigDecimal amount,
        String status,
        String roleForViewer,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
