package com.shareride.shareride_mobile_backend.ride;

public record CreateRideRequestDto(
        Long rideId,
        Integer seats
) {
}