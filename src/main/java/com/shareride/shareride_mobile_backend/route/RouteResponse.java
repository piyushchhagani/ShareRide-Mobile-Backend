package com.shareride.shareride_mobile_backend.route;

import java.util.List;

public record RouteResponse(
        double distanceMeters,
        double durationSeconds,
        List<List<Double>> coordinates,
        boolean approximate
) {
    /** Backwards-compatible constructor for ride matching responses. */
    public RouteResponse(
            double distanceMeters,
            double durationSeconds,
            List<List<Double>> coordinates
    ) {
        this(distanceMeters, durationSeconds, coordinates, false);
    }
}
