package com.shareride.shareride_mobile_backend.route;

import jakarta.validation.constraints.NotNull;

public record RouteRequest(
        @NotNull(message = "Pickup latitude is required")
        Double pickupLatitude,

        @NotNull(message = "Pickup longitude is required")
        Double pickupLongitude,

        @NotNull(message = "Destination latitude is required")
        Double destinationLatitude,

        @NotNull(message = "Destination longitude is required")
        Double destinationLongitude
) {}