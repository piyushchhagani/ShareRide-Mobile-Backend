package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.route.RouteRequest;
import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rides")
public class RideController {

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

    @PostMapping
    public ResponseEntity<?> createRide(
            @RequestBody CreateRideRequest request,
            Authentication authentication
    ) {

        User driver = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

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

        Ride saved = rideRepository.save(ride);

        return ResponseEntity.ok(toResponse(saved));
    }
    @GetMapping("/{rideId}")
        public ResponseEntity<?> getRide(@PathVariable Long rideId) {
        return rideRepository.findById(rideId)
                .map(ride -> ResponseEntity.ok(toResponse(ride)))
                .orElseGet(() -> ResponseEntity.notFound().build());
        }
 
    @GetMapping
    public ResponseEntity<?> getAllRides() {

        return ResponseEntity.ok(
                rideRepository
                        .findByStatusOrderByDepartureTimeAsc("ACTIVE")
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @GetMapping("/mine")
    public ResponseEntity<?> myRides(
            Authentication authentication
    ) {

        User driver = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        return ResponseEntity.ok(
                rideRepository
                        .findByDriverId(driver.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @PostMapping("/find")
    public ResponseEntity<?> findRides(
            @RequestBody FindRideRequest request
    ) {

        RouteResponse passengerRoute =
                routeService.calculateRoute(
                        new RouteRequest(
                                request.pickupLatitude(),
                                request.pickupLongitude(),
                                request.destinationLatitude(),
                                request.destinationLongitude()
                        )
                );

        var results = rideRepository
                .findByStatus("ACTIVE")
                .stream()

                .filter(ride ->
                        ride.getAvailableSeats()
                                >= request.seats()
                )

                .filter(ride -> {

                    long minutes =
                            Math.abs(
                                    java.time.Duration.between(
                                            ride.getDepartureTime(),
                                            request.departureTime()
                                    ).toMinutes()
                            );

                    return minutes <= 60;
                })

                .map(ride -> {

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
                })

                .filter(result ->
                        result.score().totalScore() >= 35
                )

                .sorted(
                        (a, b) ->
                                Double.compare(
                                        b.score().totalScore(),
                                        a.score().totalScore()
                                )
                )

                .map(result ->
                        toResponse(
                                result.ride(),
                                result.score()
                        )
                )

                .toList();

        return ResponseEntity.ok(results);
    }

    private record ScoredRide(
            Ride ride,
            MatchScore score
    ) {
    }

    private RideResponse toResponse(Ride ride) {

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
}