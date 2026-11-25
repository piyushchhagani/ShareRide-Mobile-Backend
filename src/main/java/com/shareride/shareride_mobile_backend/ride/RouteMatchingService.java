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
     * 1.0 km corridor threshold balances parallel campus/city roads
     * with meaningful route similarity when matching partial legs.
     */
    private static final double ROUTE_MATCH_THRESHOLD_KM = 1.0;
    private static final int FALLBACK_SEGMENTS = 20;

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

        if (ride == null) {
            throw new IllegalArgumentException("Ride is required for route matching");
        }

        if (requestedTime == null) {
            throw new IllegalArgumentException("Requested departure time is required");
        }

        RouteResponse safePassengerRoute = ensureUsableRoute(
                passengerRoute,
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude()
        );

        RouteResponse driverRoute =
                calculateDriverRoute(ride);

        double routeScore =
                calculateRouteSimilarity(
                        safePassengerRoute.coordinates(),
                        driverRoute.coordinates()
                );

        List<Double> passengerPickup = safePassengerRoute.coordinates().get(0);

        double pickupScore =
                calculateDistanceScore(
                        ride.getPickupLatitude(),
                        ride.getPickupLongitude(),
                        passengerPickup.get(1),
                        passengerPickup.get(0)
                );

        List<Double> passengerDestination =
                safePassengerRoute.coordinates()
                        .get(
                                safePassengerRoute.coordinates().size() - 1
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
                        safePassengerRoute.distanceMeters(),
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

    private RouteResponse ensureUsableRoute(
            RouteResponse route,
            double pickupLatitude,
            double pickupLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        if (route == null
                || route.coordinates() == null
                || route.coordinates().size() < 2
                || !isCoordinate(route.coordinates().get(0))
                || !isCoordinate(route.coordinates().get(route.coordinates().size() - 1))) {
            return createFallbackRoute(new RouteRequest(
                    pickupLatitude,
                    pickupLongitude,
                    destinationLatitude,
                    destinationLongitude
            ));
        }

        return route;
    }

    private boolean isCoordinate(List<Double> point) {
        return point != null
                && point.size() >= 2
                && point.get(0) != null
                && point.get(1) != null;
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

            return new RouteResponse(
                    routeResult.getDistanceMeters(),
                    routeResult.getDurationSeconds(),
                    routeResult.getPoints().stream()
                            .map(point -> List.of(point.getLongitude(), point.getLatitude()))
                            .toList()
            );

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

        List<List<Double>> densePassenger = densifyIfStraightLine(passengerRoute);
        List<List<Double>> denseDriver = densifyIfStraightLine(driverRoute);

        double passengerOverlap = calculateDirectionalOverlap(densePassenger, denseDriver);
        double driverOverlap = calculateDirectionalOverlap(denseDriver, densePassenger);
        return Math.max(passengerOverlap, driverOverlap);
    }

    private List<List<Double>> densifyIfStraightLine(List<List<Double>> route) {
        if (route.size() != 2 || !isCoordinate(route.get(0)) || !isCoordinate(route.get(1))) {
            return route;
        }
        return interpolateSegment(
                route.get(0).get(1),
                route.get(0).get(0),
                route.get(1).get(1),
                route.get(1).get(0)
        );
    }

    private double calculateDirectionalOverlap(
            List<List<Double>> sourceRoute,
            List<List<Double>> targetRoute
    ) {
        int samples = Math.min(sourceRoute.size(), 50);
        int matchedPoints = 0;

        for (int i = 0; i < samples; i++) {
            int sourceIndex = (int) ((long) i * sourceRoute.size() / samples);
            List<Double> sourcePoint = sourceRoute.get(Math.min(sourceIndex, sourceRoute.size() - 1));
            if (sourcePoint == null || sourcePoint.size() < 2) {
                continue;
            }

            double minimumDistance = Double.MAX_VALUE;
            for (List<Double> targetPoint : targetRoute) {
                if (targetPoint == null || targetPoint.size() < 2) {
                    continue;
                }
                double distance = haversine(
                        sourcePoint.get(1),
                        sourcePoint.get(0),
                        targetPoint.get(1),
                        targetPoint.get(0)
                );
                minimumDistance = Math.min(minimumDistance, distance);
            }

            if (minimumDistance <= ROUTE_MATCH_THRESHOLD_KM) {
                matchedPoints++;
            }
        }

        return samples == 0 ? 0 : matchedPoints * 100.0 / samples;
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
        if (difference <= 90) return 20;
        if (difference <= 120) return 10;

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

        List<List<Double>> coordinates = interpolateSegment(
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.destinationLatitude(),
                request.destinationLongitude()
        );

        return new RouteResponse(
                distanceMeters,
                durationSeconds,
                coordinates
        );
    }

    private List<List<Double>> interpolateSegment(
            double startLat,
            double startLon,
            double endLat,
            double endLon
    ) {
        List<List<Double>> points = new java.util.ArrayList<>(FALLBACK_SEGMENTS + 1);
        for (int i = 0; i <= FALLBACK_SEGMENTS; i++) {
            double t = (double) i / FALLBACK_SEGMENTS;
            double lat = startLat + (endLat - startLat) * t;
            double lon = startLon + (endLon - startLon) * t;
            points.add(List.of(lon, lat));
        }
        return points;
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
