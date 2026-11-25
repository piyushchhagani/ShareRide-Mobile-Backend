package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.notification.NotificationService;
import com.shareride.shareride_mobile_backend.payment.FareService;
import com.shareride.shareride_mobile_backend.rating.RatingRepository;
import com.shareride.shareride_mobile_backend.route.RouteRequest;
import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private static final Logger log =
            LoggerFactory.getLogger(RideController.class);

    private static final long MAX_DEPARTURE_DIFFERENCE_MINUTES = 120;
    private static final double MIN_MATCH_SCORE = 25.0;
    private static final double MIN_ROUTE_SCORE = 30.0;
    private static final double FALLBACK_AVERAGE_SPEED_KMH = 30.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RideRepository rideRepository;
    private final RideRequestRepository rideRequestRepository;
    private final UserRepository userRepository;
    private final RouteService routeService;
    private final RouteMatchingService routeMatchingService;
    private final NotificationService notificationService;
    private final RatingRepository ratingRepository;
    private final FareService fareService;

    public RideController(
            RideRepository rideRepository,
            RideRequestRepository rideRequestRepository,
            UserRepository userRepository,
            RouteService routeService,
            RouteMatchingService routeMatchingService,
            NotificationService notificationService,
            RatingRepository ratingRepository,
            FareService fareService
    ) {
        this.rideRepository = rideRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.userRepository = userRepository;
        this.routeService = routeService;
        this.routeMatchingService = routeMatchingService;
        this.notificationService = notificationService;
        this.ratingRepository = ratingRepository;
        this.fareService = fareService;
    }

    // -------------------------------------------------------------------------
    // CREATE RIDE
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<RideResponse> createRide(
            @jakarta.validation.Valid @RequestBody CreateRideRequest request,
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
        ride.setPricePerSeat(
                request.pricePerSeat() != null
                        ? request.pricePerSeat()
                        : fareService.suggestFarePerSeat(
                                request.pickupLatitude(),
                                request.pickupLongitude(),
                                request.destinationLatitude(),
                                request.destinationLongitude()
                        )
        );

        Ride savedRide = rideRepository.save(ride);

        log.info(
                "Ride created successfully: rideId={}, driverId={}",
                savedRide.getId(),
                driver.getId()
        );

        return ResponseEntity.ok(toResponse(savedRide));
    }

    // -------------------------------------------------------------------------
    // UPDATE RIDE STATUS (CANCEL / COMPLETE)
    // -------------------------------------------------------------------------

    @PatchMapping("/{rideId}/status")
    public ResponseEntity<?> updateRideStatus(
            @PathVariable @NonNull Long rideId,
            @jakarta.validation.Valid @RequestBody UpdateRideStatusRequest request,
            Authentication authentication
    ) {

        User driver = getAuthenticatedUser(authentication);

        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "Ride not found"));
        }

        if (!ride.getDriver().getId().equals(driver.getId())) {
            return ResponseEntity.status(403)
                    .body(java.util.Map.of("message", "Only the ride driver can update the ride status"));
        }

        ride.setStatus(request.status().toUpperCase());
        ride.setUpdatedAt(java.time.LocalDateTime.now());
        Ride saved = rideRepository.save(ride);

        String newStatus = saved.getStatus();
        if ("CANCELLED".equals(newStatus) || "COMPLETED".equals(newStatus)) {
            List<RideRequest> requests = rideRequestRepository.findByRideIdOrderByCreatedAtDesc(rideId);
            for (RideRequest req : requests) {
                if (("PENDING".equals(req.getStatus()) || "ACCEPTED".equals(req.getStatus()))
                        && req.getPassenger().isBookingUpdatesEnabled()) {
                    if ("CANCELLED".equals(newStatus)) {
                        notificationService.createNotification(
                                req.getPassenger(),
                                "RIDE_CANCELLED",
                                "Ride cancelled",
                                driver.getName() + " cancelled ride #" + rideId + ".",
                                saved
                        );
                    } else if ("ACCEPTED".equals(req.getStatus())) {
                        notificationService.createNotification(
                                req.getPassenger(),
                                "RIDE_COMPLETED",
                                "Ride completed",
                                "Ride #" + rideId + " with " + driver.getName() + " is marked completed. You can now rate your experience!",
                                saved
                        );
                    }
                }
            }
        }

        log.info(
                "Ride status updated: rideId={}, driverId={}, newStatus={}",
                rideId,
                driver.getId(),
                saved.getStatus()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    // -------------------------------------------------------------------------
    // GET RIDE BY ID
    // -------------------------------------------------------------------------

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRide(
            @PathVariable @NonNull Long rideId
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
            @RequestParam(required = false) List<String> status,
            Authentication authentication
    ) {

        User driver = getAuthenticatedUser(authentication);

        List<Ride> rawRides;
        if (status != null && !status.isEmpty()) {
            List<String> normalized = status.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase())
                    .toList();
            rawRides = normalized.isEmpty()
                    ? rideRepository.findByDriverIdOrderByDepartureTimeDesc(driver.getId())
                    : rideRepository.findByDriverIdAndStatusInOrderByDepartureTimeDesc(driver.getId(), normalized);
        } else {
            rawRides = rideRepository.findByDriverIdOrderByDepartureTimeDesc(driver.getId());
        }

        List<RideResponse> rides =
                rawRides
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
            @jakarta.validation.Valid @RequestBody FindRideRequest request,
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
                        .filter(ride ->
                                matchesVehicleType(
                                        ride,
                                        request.vehicleType()
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
        java.util.Comparator<ScoredRide> comparator = createSearchComparator(request.sortBy());

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
                                scoredRide.score().routeScore()
                                        >= MIN_ROUTE_SCORE
                                        && scoredRide.score().totalScore()
                                        >= MIN_MATCH_SCORE
                        )
                        .sorted(comparator)
                        .map(scoredRide ->
                                toResponse(
                                        scoredRide.ride(),
                                        scoredRide.score()
                                )
                        )
                        .toList();

        if (request.page() != null && request.size() != null && request.page() >= 0 && request.size() > 0) {
            int fromIndex = request.page() * request.size();
            if (fromIndex >= results.size()) {
                results = List.of();
            } else {
                int toIndex = Math.min(results.size(), fromIndex + request.size());
                results = results.subList(fromIndex, toIndex);
            }
        }

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

    private boolean matchesVehicleType(Ride ride, String vehicleTypeFilter) {
        if (vehicleTypeFilter == null || vehicleTypeFilter.isBlank() || "ALL".equalsIgnoreCase(vehicleTypeFilter.trim())) {
            return true;
        }
        if (ride.getVehicleType() == null) {
            return false;
        }
        return ride.getVehicleType().toLowerCase().contains(vehicleTypeFilter.trim().toLowerCase());
    }

    private java.util.Comparator<ScoredRide> createSearchComparator(String sortBy) {
        if ("PRICE_ASC".equalsIgnoreCase(sortBy)) {
            return java.util.Comparator.comparing(
                    (ScoredRide sr) -> sr.ride().getPricePerSeat() != null
                            ? sr.ride().getPricePerSeat()
                            : java.math.BigDecimal.valueOf(Double.MAX_VALUE)
            ).thenComparing((first, second) ->
                    Double.compare(second.score().totalScore(), first.score().totalScore()));
        }
        if ("DEPARTURE_ASC".equalsIgnoreCase(sortBy)) {
            return java.util.Comparator.comparing(
                    (ScoredRide sr) -> sr.ride().getDepartureTime()
            ).thenComparing((first, second) ->
                    Double.compare(second.score().totalScore(), first.score().totalScore()));
        }
        return (first, second) ->
                Double.compare(second.score().totalScore(), first.score().totalScore());
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
        return toResponse(ride, null);
    }

    private RideResponse toResponse(
            Ride ride,
            MatchScore score
    ) {
        Long driverId = ride.getDriver().getId();
        Double rawAvg = ratingRepository.findAverageScoreByRatedUserId(driverId);
        long totalRatings = ratingRepository.countByRatedUserId(driverId);
        Double avg = (rawAvg != null && totalRatings > 0)
                ? Math.round(rawAvg * 10.0) / 10.0
                : null;

        return new RideResponse(
                ride.getId(),
                driverId,
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
                ride.getPricePerSeat(),

                ride.getStatus(),

                score != null ? score.totalScore() : null,
                score != null ? score.routeScore() : null,
                score != null ? score.timeScore() : null,
                score != null ? score.pickupScore() : null,
                score != null ? score.destinationScore() : null,
                score != null ? score.detourScore() : null,

                avg,
                totalRatings
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
