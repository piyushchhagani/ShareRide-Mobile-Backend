package com.shareride.shareride_mobile_backend.route;

public record RouteRequest(
        double pickupLatitude,
        double pickupLongitude,
        double destinationLatitude,
        double destinationLongitude
) {}