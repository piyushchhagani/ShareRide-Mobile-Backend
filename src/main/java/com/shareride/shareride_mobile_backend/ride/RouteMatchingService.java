package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.route.RouteRequest;
import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RouteMatchingService {

    private final RouteService routeService;

    public RouteMatchingService(RouteService routeService) {
        this.routeService = routeService;
    }

    public MatchScore calculate(
            Ride ride,
            RouteResponse passengerRoute,
            LocalDateTime requestedTime
    ) {

        RouteResponse driverRoute =
                routeService.calculateRoute(
                        new RouteRequest(
                                ride.getPickupLatitude(),
                                ride.getPickupLongitude(),
                                ride.getDestinationLatitude(),
                                ride.getDestinationLongitude()
                        )
                );

        double routeScore = calculateRouteSimilarity(
                passengerRoute.coordinates(),
                driverRoute.coordinates()
        );

        double pickupScore = calculateDistanceScore(
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                passengerRoute.coordinates().get(0).get(1),
                passengerRoute.coordinates().get(0).get(0)
        );

        List<Double> destination =
                passengerRoute.coordinates()
                        .get(
                                passengerRoute.coordinates().size() - 1
                        );

        double destinationScore = calculateDistanceScore(
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),
                destination.get(1),
                destination.get(0)
        );

        double timeScore = calculateTimeScore(
                ride.getDepartureTime(),
                requestedTime
        );

        double detourScore = calculateDetourScore(
                passengerRoute.distanceMeters(),
                driverRoute.distanceMeters()
        );

        double totalScore =
                (routeScore * 0.40)
                        + (timeScore * 0.20)
                        + (pickupScore * 0.15)
                        + (destinationScore * 0.10)
                        + (detourScore * 0.15);

        return new MatchScore(
                round(totalScore),
                round(routeScore),
                round(timeScore),
                round(pickupScore),
                round(destinationScore),
                round(detourScore)
        );
    }

    private double calculateRouteSimilarity(
            List<List<Double>> passengerRoute,
            List<List<Double>> driverRoute
    ) {

        if (passengerRoute.isEmpty() || driverRoute.isEmpty()) {
            return 0;
        }

        int samples =
                Math.min(passengerRoute.size(), 100);

        int matchedPoints = 0;

        for (int i = 0; i < samples; i++) {

            int index =
                    (int) (
                            (long) i
                                    * passengerRoute.size()
                                    / samples
                    );

            List<Double> passengerPoint =
                    passengerRoute.get(
                            Math.min(
                                    index,
                                    passengerRoute.size() - 1
                            )
                    );

            double minimumDistance =
                    Double.MAX_VALUE;

            for (List<Double> driverPoint : driverRoute) {

                double distance =
                        haversine(
                                passengerPoint.get(1),
                                passengerPoint.get(0),
                                driverPoint.get(1),
                                driverPoint.get(0)
                        );

                minimumDistance =
                        Math.min(
                                minimumDistance,
                                distance
                        );
            }

            if (minimumDistance <= 1.0) {
                matchedPoints++;
            }
        }

        return matchedPoints * 100.0 / samples;
    }

    private double calculateDetourScore(
            double passengerDistanceMeters,
            double driverDistanceMeters
    ) {

        if (passengerDistanceMeters <= 0) {
            return 0;
        }

        double extraDistance =
                Math.max(
                        0,
                        driverDistanceMeters
                                - passengerDistanceMeters
                );

        double detourPercentage =
                extraDistance
                        / passengerDistanceMeters
                        * 100;

        if (detourPercentage <= 5) return 100;
        if (detourPercentage <= 10) return 90;
        if (detourPercentage <= 15) return 75;
        if (detourPercentage <= 20) return 60;
        if (detourPercentage <= 30) return 40;
        if (detourPercentage <= 40) return 20;

        return 0;
    }

    private double calculateTimeScore(
            LocalDateTime rideTime,
            LocalDateTime requestedTime
    ) {

        long difference =
                Math.abs(
                        Duration.between(
                                rideTime,
                                requestedTime
                        ).toMinutes()
                );

        if (difference <= 10) return 100;
        if (difference <= 20) return 90;
        if (difference <= 30) return 75;
        if (difference <= 45) return 55;
        if (difference <= 60) return 35;

        return 0;
    }

    private double calculateDistanceScore(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double distance =
                haversine(
                        lat1,
                        lon1,
                        lat2,
                        lon2
                );

        if (distance <= 0.5) return 100;
        if (distance <= 1.0) return 90;
        if (distance <= 2.0) return 75;
        if (distance <= 5.0) return 50;
        if (distance <= 10.0) return 25;

        return 0;
    }

    private double haversine(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        final double earthRadius = 6371.0;

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLon =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2)
                        * Math.sin(dLat / 2)
                        +
                        Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadius * c;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}