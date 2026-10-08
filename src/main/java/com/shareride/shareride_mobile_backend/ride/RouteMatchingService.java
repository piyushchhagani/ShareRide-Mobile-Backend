package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.route.RouteRequest;
import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RouteMatchingService {

    private static final Logger log =
            LoggerFactory.getLogger(RouteMatchingService.class);

    private static final double ROUTE_WEIGHT = 0.40;
    private static final double TIME_WEIGHT = 0.20;
    private static final double PICKUP_WEIGHT = 0.15;
    private static final double DESTINATION_WEIGHT = 0.10;
    private static final double DETOUR_WEIGHT = 0.15;

    /*
     * A 1-meter threshold is too strict for route comparison.
     * 500 meters gives us a practical similarity comparison
     * while still being meaningful for campus ride sharing.
     */
    private static final double ROUTE_MATCH_THRESHOLD_METERS = 500.0;

    private static final double FALLBACK_SPEED_KMH = 30.0;

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
                calculateDriverRoute(ride);

        double routeScore =
                calculateRouteSimilarity(
                        passengerRoute.coordinates(),
                        driverRoute.coordinates()
                );

        double pickupScore =
                calculateDistanceScore(
                        ride.getPickupLatitude(),
                        ride.getPickupLongitude(),
                        passengerRoute.coordinates()
                                .get(0)
                                .get(1),
                        passengerRoute.coordinates()
                                .get(0)
                                .get(0)
                );

        List<Double> passengerDestination =
                passengerRoute.coordinates()
                        .get(
                                passengerRoute.coordinates().size() - 1
                        );

        double destinationScore =
                calculateDistanceScore(
                        ride.getDestinationLatitude(),
                        ride.getDestinationLongitude(),
                        passengerDestination.get(1),
                        passengerDestination.get(0)
                );

        double timeScore =
                calculateTimeScore(
                        ride.getDepartureTime(),
                        requestedTime
                );

        double detourScore =
                calculateDetourScore(
                        passengerRoute.distanceMeters(),
                        driverRoute.distanceMeters()
                );

        double totalScore =
                (routeScore * ROUTE_WEIGHT)
                        + (timeScore * TIME_WEIGHT)
                        + (pickupScore * PICKUP_WEIGHT)
                        + (destinationScore * DESTINATION_WEIGHT)
                        + (detourScore * DETOUR_WEIGHT);

        MatchScore score =
                new MatchScore(
                        round(totalScore),
                        round(routeScore),
                        round(timeScore),
                        round(pickupScore),
                        round(destinationScore),
                        round(detourScore)
                );

        log.info(
                "Ride match calculated: rideId={}, total={}, route={}, time={}, pickup={}, destination={}, detour={}",
                ride.getId(),
                score.totalScore(),
                score.routeScore(),
                score.timeScore(),
                score.pickupScore(),
                score.destinationScore(),
                score.detourScore()
        );

        return score;
    }

    private RouteResponse calculateDriverRoute(
            Ride ride
    ) {

        RouteRequest request =
                new RouteRequest(
                        ride.getPickupLatitude(),
                        ride.getPickupLongitude(),
                        ride.getDestinationLatitude(),
                        ride.getDestinationLongitude()
                );

        try {

            RouteService.RouteResult routeResult =
                    routeService.calculateRoute(
                            ride.getPickupLatitude(),
                            ride.getPickupLongitude(),
                            ride.getDestinationLatitude(),
                            ride.getDestinationLongitude()
                    );

            RouteResponse route =
                    new RouteResponse(
                            extractDistanceMeters(routeResult),
                            extractDurationSeconds(routeResult),
                            extractCoordinates(routeResult)
                    );

            log.debug(
                    "Driver route calculated using routing service: rideId={}",
                    ride.getId()
            );

            return route;

        } catch (Exception exception) {

            log.warn(
                    "Driver route unavailable. " +
                    "Using fallback route: rideId={}, reason={}",
                    ride.getId(),
                    exception.getMessage()
            );

            return createFallbackRoute(request);
        }
    }

    private double calculateRouteSimilarity(
            List<List<Double>> passengerRoute,
            List<List<Double>> driverRoute
    ) {

        if (passengerRoute == null
                || driverRoute == null
                || passengerRoute.isEmpty()
                || driverRoute.isEmpty()) {

            return 0;
        }

        int samples =
                Math.min(
                        passengerRoute.size(),
                        50
                );

        int matchedPoints = 0;

        for (int i = 0; i < samples; i++) {

            int passengerIndex =
                    (int) (
                            (long) i
                                    * passengerRoute.size()
                                    / samples
                    );

            List<Double> passengerPoint =
                    passengerRoute.get(
                            Math.min(
                                    passengerIndex,
                                    passengerRoute.size() - 1
                            )
                    );

            double minimumDistance =
                    Double.MAX_VALUE;

            for (List<Double> driverPoint : driverRoute) {

                if (driverPoint == null
                        || driverPoint.size() < 2) {
                    continue;
                }

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

            if (minimumDistance
                    <= ROUTE_MATCH_THRESHOLD_METERS) {

                matchedPoints++;
            }
        }

        return samples == 0
                ? 0
                : matchedPoints * 100.0 / samples;
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

        /*
         * Distance is measured in kilometers here.
         */
        if (distance <= 0.5) return 100;
        if (distance <= 1.0) return 90;
        if (distance <= 2.0) return 75;
        if (distance <= 5.0) return 50;
        if (distance <= 10.0) return 25;

        return 0;
    }

    private double extractDistanceMeters(
            RouteService.RouteResult routeResult
    ) {
        return extractNumericValue(
                routeResult,
                "distanceMeters",
                "distance",
                "distanceKm",
                "distanceInMeters",
                "distanceValue"
        );
    }

    private double extractDurationSeconds(
            RouteService.RouteResult routeResult
    ) {
        return extractNumericValue(
                routeResult,
                "durationSeconds",
                "duration",
                "durationInSeconds",
                "estimatedSeconds",
                "timeSeconds",
                "seconds"
        );
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> extractCoordinates(
            RouteService.RouteResult routeResult
    ) {
        Object value =
                extractValue(
                        routeResult,
                        "coordinates",
                        "routeCoordinates",
                        "points",
                        "path"
                );

        if (value instanceof List<?>) {
            return (List<List<Double>>) value;
        }

        return java.util.Collections.emptyList();
    }

    private double extractNumericValue(
            Object target,
            String... candidateNames
    ) {
        Object value =
                extractValue(
                        target,
                        candidateNames
                );

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return 0.0;
    }

    private Object extractValue(
            Object target,
            String... candidateNames
    ) {
        if (target == null) {
            return null;
        }

        Class<?> targetClass = target.getClass();

        for (String candidateName : candidateNames) {
            String capitalized =
                    Character.toUpperCase(candidateName.charAt(0))
                            + candidateName.substring(1);

            for (String methodName : new String[] {
                    candidateName,
                    "get" + capitalized,
                    "is" + capitalized
            }) {
                try {
                    java.lang.reflect.Method method =
                            targetClass.getMethod(methodName);
                    Object value = method.invoke(target);
                    if (value != null) {
                        return value;
                    }
                } catch (NoSuchMethodException
                         | IllegalAccessException
                         | java.lang.reflect.InvocationTargetException ignored) {
                    // Ignore and continue to the next candidate.
                }
            }

            try {
                java.lang.reflect.Field field =
                        targetClass.getDeclaredField(candidateName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (NoSuchFieldException
                     | IllegalAccessException ignored) {
                // Ignore and continue to the next candidate.
            }
        }

        return null;
    }

    private RouteResponse createFallbackRoute(
            RouteRequest request
    ) {

        double distanceMeters =
                haversineDistanceMeters(
                        request.pickupLatitude(),
                        request.pickupLongitude(),
                        request.destinationLatitude(),
                        request.destinationLongitude()
                );

        double durationSeconds =
                (distanceMeters / 1000.0)
                        / FALLBACK_SPEED_KMH
                        * 3600.0;

        List<List<Double>> coordinates =
                List.of(
                        List.of(
                                request.pickupLongitude(),
                                request.pickupLatitude()
                        ),
                        List.of(
                                request.destinationLongitude(),
                                request.destinationLatitude()
                        )
                );

        return new RouteResponse(
                distanceMeters,
                durationSeconds,
                coordinates
        );
    }

    private double haversine(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        final double earthRadiusKm = 6371.0;

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

        return earthRadiusKm * c;
    }

    private double haversineDistanceMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        return haversine(
                lat1,
                lon1,
                lat2,
                lon2
        ) * 1000.0;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}