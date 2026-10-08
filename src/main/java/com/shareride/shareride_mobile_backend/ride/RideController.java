package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.route.RouteRequest;
import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private static final Logger log =
            LoggerFactory.getLogger(RideController.class);

    private static final long MAX_DEPARTURE_DIFFERENCE_MINUTES = 60;
    private static final double MIN_MATCH_SCORE = 35.0;
    private static final double FALLBACK_AVERAGE_SPEED_KMH = 30.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final RouteService routeService;
    private final RouteMatchingService routeMatchingService;

    public RideController(
            RideRepository rideRepository,
            UserRepository userRepository,
            RouteService routeService,
            RouteMatchingService routeMatchingService
    ) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.routeService = routeService;
        this.routeMatchingService = routeMatchingService;
    }

    // -------------------------------------------------------------------------
    // CREATE RIDE
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<RideResponse> createRide(
            @RequestBody CreateRideRequest request,
            Authentication authentication
    ) {

        User driver = getAuthenticatedUser(authentication);

        Ride ride = new Ride();

        ride.setDriver(driver);

        ride.setPickupAddress(request.pickupAddress());
        ride.setPickupLatitude(request.pickupLatitude());
        ride.setPickupLongitude(request.pickupLongitude());

        ride.setDestinationAddress(request.destinationAddress());
        ride.setDestinationLatitude(request.destinationLatitude());
        ride.setDestinationLongitude(request.destinationLongitude());

        ride.setDepartureTime(request.departureTime());
        ride.setAvailableSeats(request.availableSeats());

        ride.setVehicleType(request.vehicleType());
        ride.setVehicleNumber(request.vehicleNumber());

        Ride savedRide = rideRepository.save(ride);

        log.info(
                "Ride created successfully: rideId={}, driverId={}",
                savedRide.getId(),
                driver.getId()
        );

        return ResponseEntity.ok(toResponse(savedRide));
    }

    // -------------------------------------------------------------------------
    // GET RIDE BY ID
    // -------------------------------------------------------------------------

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRide(
            @PathVariable Long rideId
    ) {

        return rideRepository
                .findById(rideId)
                .map(ride -> ResponseEntity.ok(toResponse(ride)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // GET ALL ACTIVE RIDES
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<RideResponse>> getAllRides() {

        List<RideResponse> rides =
                rideRepository
                        .findByStatusOrderByDepartureTimeAsc("ACTIVE")
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(rides);
    }

    // -------------------------------------------------------------------------
    // GET CURRENT USER'S RIDES
    // -------------------------------------------------------------------------

    @GetMapping("/mine")
    public ResponseEntity<List<RideResponse>> getMyRides(
            Authentication authentication
    ) {

        User driver = getAuthenticatedUser(authentication);

        List<RideResponse> rides =
                rideRepository
                        .findByDriverId(driver.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(rides);
    }

    // -------------------------------------------------------------------------
    // FIND MATCHING RIDES
    // -------------------------------------------------------------------------

    @PostMapping("/find")
    public ResponseEntity<List<RideResponse>> findRides(
            @RequestBody FindRideRequest request,
            Authentication authentication
    ) {

        User passenger = getAuthenticatedUser(authentication);

        log.info(
                "Ride search started: passengerId={}, seats={}, departureTime={}",
                passenger.getId(),
                request.seats(),
                request.departureTime()
        );

        /*
         * STEP 1
         *
         * Retrieve active rides and apply inexpensive filters first.
         *
         * A passenger must never see their own rides as matching
         * rides because they cannot request their own ride.
         */
        List<Ride> candidates =
                rideRepository
                        .findByStatus("ACTIVE")
                        .stream()
                        .filter(ride ->
                                !ride.getDriver().getId().equals(passenger.getId())
                        )
                        .filter(ride ->
                                hasEnoughSeats(
                                        ride,
                                        request.seats()
                                )
                        )
                        .filter(ride ->
                                isWithinDepartureWindow(
                                        ride,
                                        request.departureTime()
                                )
                        )
                        .toList();

        log.info(
                "Ride search candidates: {}",
                candidates.size()
        );

        /*
         * No suitable rides means there is nothing to score.
         */
        if (candidates.isEmpty()) {

            log.info("No rides matched basic search criteria.");

            return ResponseEntity.ok(List.of());
        }

        /*
         * STEP 2
         *
         * Calculate the passenger route.
         *
         * ORS is treated as an enhancement, not a hard dependency.
         * If ORS is unavailable, the matching process continues using
         * a straight-line fallback route.
         */
        RouteResponse passengerRoute =
                calculatePassengerRoute(request);

        /*
         * STEP 3
         *
         * Calculate the compatibility score for each candidate.
         */
        List<RideResponse> results =
                candidates
                        .stream()
                        .map(ride ->
                                scoreRide(
                                        ride,
                                        passengerRoute,
                                        request
                                )
                        )
                        .filter(scoredRide ->
                                scoredRide.score().totalScore()
                                        >= MIN_MATCH_SCORE
                        )
                        .sorted(
                                (first, second) ->
                                        Double.compare(
                                                second.score().totalScore(),
                                                first.score().totalScore()
                                        )
                        )
                        .map(scoredRide ->
                                toResponse(
                                        scoredRide.ride(),
                                        scoredRide.score()
                                )
                        )
                        .toList();

        log.info(
                "Ride search completed: candidates={}, matches={}",
                candidates.size(),
                results.size()
        );

        return ResponseEntity.ok(results);
    }

    // -------------------------------------------------------------------------
    // MATCHING HELPERS
    // -------------------------------------------------------------------------

    private boolean hasEnoughSeats(
            Ride ride,
            int requestedSeats
    ) {

        return ride.getAvailableSeats() >= requestedSeats;
    }

    private boolean isWithinDepartureWindow(
            Ride ride,
            java.time.LocalDateTime requestedDepartureTime
    ) {

        long differenceMinutes =
                Math.abs(
                        Duration.between(
                                ride.getDepartureTime(),
                                requestedDepartureTime
                        ).toMinutes()
                );

        return differenceMinutes
                <= MAX_DEPARTURE_DIFFERENCE_MINUTES;
    }

    private ScoredRide scoreRide(
            Ride ride,
            RouteResponse passengerRoute,
            FindRideRequest request
    ) {

        MatchScore score =
                routeMatchingService.calculate(
                        ride,
                        passengerRoute,
                        request.departureTime()
                );

        return new ScoredRide(
                ride,
                score
        );
    }

    // -------------------------------------------------------------------------
    // ROUTE CALCULATION
    // -------------------------------------------------------------------------

    private RouteResponse calculatePassengerRoute(
            FindRideRequest request
    ) {

        RouteRequest routeRequest =
                new RouteRequest(
                        request.pickupLatitude(),
                        request.pickupLongitude(),
                        request.destinationLatitude(),
                        request.destinationLongitude()
                );

        try {

            RouteService.RouteResult routeResult =
                    routeService.calculateRoute(
                            request.pickupLatitude(),
                            request.pickupLongitude(),
                            request.destinationLatitude(),
                            request.destinationLongitude()
                    );

            log.info(
                    "Passenger route calculated successfully using routing service."
            );

            return toRouteResponse(routeResult);

        } catch (Exception exception) {

            log.warn(
                    "Routing service unavailable. " +
                    "Using fallback route. reason={}",
                    exception.getMessage()
            );

            return createFallbackRoute(routeRequest);
        }
    }

    /**
     * Creates a straight-line route when the external routing service
     * is unavailable.
     *
     * This fallback is intentionally simple. It keeps ride matching
     * functional but should not be used for navigation.
     */
    private RouteResponse createFallbackRoute(
            RouteRequest request
    ) {

        double distanceMeters =
                calculateHaversineDistance(
                        request.pickupLatitude(),
                        request.pickupLongitude(),
                        request.destinationLatitude(),
                        request.destinationLongitude()
                );

        double durationSeconds =
                calculateFallbackDuration(distanceMeters);

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

        log.debug(
                "Fallback route created: distanceMeters={}, durationSeconds={}",
                distanceMeters,
                durationSeconds
        );

        return new RouteResponse(
                distanceMeters,
                durationSeconds,
                coordinates
        );
    }

    private double calculateFallbackDuration(
            double distanceMeters
    ) {

        return (
                distanceMeters / 1_000.0
        ) / FALLBACK_AVERAGE_SPEED_KMH * 3_600.0;
    }

    /**
     * Haversine distance between two geographic coordinates.
     */
    private double calculateHaversineDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        double latitude1Radians =
                Math.toRadians(latitude1);

        double latitude2Radians =
                Math.toRadians(latitude2);

        double latitudeDifference =
                Math.toRadians(latitude2 - latitude1);

        double longitudeDifference =
                Math.toRadians(longitude2 - longitude1);

        double a =
                Math.pow(
                        Math.sin(latitudeDifference / 2),
                        2
                )
                +
                Math.cos(latitude1Radians)
                        * Math.cos(latitude2Radians)
                        * Math.pow(
                                Math.sin(longitudeDifference / 2),
                                2
                        );

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_METERS * c;
    }

    // -------------------------------------------------------------------------
    // USER
    // -------------------------------------------------------------------------

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Authenticated user was not found."
                        )
                );
    }

    // -------------------------------------------------------------------------
    // RESPONSE MAPPING
    // -------------------------------------------------------------------------

    private RouteResponse toRouteResponse(
            RouteService.RouteResult routeResult
    ) {

        List<List<Double>> coordinates =
                routeResult
                        .getPoints()
                        .stream()
                        .map(point ->
                                List.of(
                                        point.getLongitude(),
                                        point.getLatitude()
                                )
                        )
                        .toList();

        return new RouteResponse(
                routeResult.getDistanceMeters(),
                routeResult.getDurationSeconds(),
                coordinates
        );
    }

    private RideResponse toResponse(
            Ride ride
    ) {

        return new RideResponse(
                ride.getId(),
                ride.getDriver().getId(),
                ride.getDriver().getName(),

                ride.getPickupAddress(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),

                ride.getDestinationAddress(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),

                ride.getDepartureTime(),

                ride.getAvailableSeats(),

                ride.getVehicleType(),
                ride.getVehicleNumber(),

                ride.getStatus(),

                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private RideResponse toResponse(
            Ride ride,
            MatchScore score
    ) {

        return new RideResponse(
                ride.getId(),
                ride.getDriver().getId(),
                ride.getDriver().getName(),

                ride.getPickupAddress(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),

                ride.getDestinationAddress(),
                ride.getDestinationLatitude(),
                ride.getDestinationLongitude(),

                ride.getDepartureTime(),

                ride.getAvailableSeats(),

                ride.getVehicleType(),
                ride.getVehicleNumber(),

                ride.getStatus(),

                score.totalScore(),
                score.routeScore(),
                score.timeScore(),
                score.pickupScore(),
                score.destinationScore(),
                score.detourScore()
        );
    }

    // -------------------------------------------------------------------------
    // INTERNAL RESULT TYPE
    // -------------------------------------------------------------------------

    private record ScoredRide(
            Ride ride,
            MatchScore score
    ) {
    }
}