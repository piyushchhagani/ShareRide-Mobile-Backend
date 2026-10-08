package com.shareride.shareride_mobile_backend.route;

import java.util.List;

public record RouteResponse(
        double distanceMeters,
        double durationSeconds,
        List<List<Double>> coordinates
) {}