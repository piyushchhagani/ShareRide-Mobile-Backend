package com.shareride.shareride_mobile_backend.payment;

import com.shareride.shareride_mobile_backend.route.RouteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FareService {

    private static final double BASE_FARE_INR = 15.0;
    private static final double RATE_PER_KM_INR = 6.0;
    private static final double MIN_FARE_INR = 20.0;

    private final RouteService routeService;

    public FareService(RouteService routeService) {
        this.routeService = routeService;
    }

    public BigDecimal suggestFarePerSeat(
            double pickupLat,
            double pickupLon,
            double destLat,
            double destLon
    ) {
        double distanceMeters;
        try {
            RouteService.RouteResult route = routeService.calculateRoute(
                    pickupLat, pickupLon, destLat, destLon
            );
            distanceMeters = route.getDistanceMeters();
        } catch (Exception ex) {
            distanceMeters = haversineMeters(pickupLat, pickupLon, destLat, destLon);
        }

        double distanceKm = Math.max(0.5, distanceMeters / 1000.0);
        double raw = Math.max(MIN_FARE_INR, BASE_FARE_INR + (distanceKm * RATE_PER_KM_INR));
        long roundedTo5 = Math.round(raw / 5.0) * 5L;
        return BigDecimal.valueOf(roundedTo5).setScale(2, RoundingMode.HALF_UP);
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
