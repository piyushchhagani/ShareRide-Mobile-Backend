package com.shareride.shareride_mobile_backend.ride;

import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ride-requests")
public class RideRequestController {

    private static final Logger log = LoggerFactory.getLogger(RideRequestController.class);

    private final RideRequestRepository requestRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    public RideRequestController(
            RideRequestRepository requestRepository,
            RideRepository rideRepository,
            UserRepository userRepository
    ) {
        this.requestRepository = requestRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
    }

    // PASSENGER REQUESTS RIDE
    @PostMapping
    @Transactional
    public ResponseEntity<?> createRequest(
            @jakarta.validation.Valid @RequestBody CreateRideRequestDto request,
            Authentication authentication
    ) {

        User passenger =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (passenger == null) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "User not found"));
        }

        Ride ride =
                rideRepository
                        .findByIdForUpdate(request.rideId())
                        .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "Ride not found"));
        }

        if (!"ACTIVE".equals(ride.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "This ride is no longer accepting requests."));
        }

        if (ride.getDriver().getId()
                .equals(passenger.getId())) {

            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "You cannot request your own ride."));
        }

        int seats =
                request.seats() == null
                        ? 1
                        : request.seats();

        if (seats <= 0) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Seats must be greater than zero."));
        }

        if (ride.getAvailableSeats() < seats) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Not enough seats available."));
        }

        RideRequest existingRequest = requestRepository
                .findByRideIdAndPassengerId(
                        ride.getId(),
                        passenger.getId()
                )
                .orElse(null);

        if (existingRequest != null
                && !"REJECTED".equals(existingRequest.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "You already have an active request for this ride."));
        }

        RideRequest rideRequest = existingRequest != null
                ? existingRequest
                : new RideRequest();

        rideRequest.setRide(ride);
        rideRequest.setPassenger(passenger);
        rideRequest.setRequestedSeats(seats);
        rideRequest.setStatus("PENDING");
        rideRequest.setRespondedAt(null);

        RideRequest saved =
                requestRepository.save(rideRequest);

        return ResponseEntity.ok(
                toResponse(saved)
        );
    }

    // PASSENGER BOOKINGS
    @GetMapping("/mine")
    public ResponseEntity<?> myRequests(
            Authentication authentication
    ) {

        User passenger =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (passenger == null) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "User not found"));
        }

        return ResponseEntity.ok(
                requestRepository
                        .findByPassengerId(passenger.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // DRIVER SEES REQUESTS
    @GetMapping("/ride/{rideId}")
    public ResponseEntity<?> rideRequests(
            @PathVariable @NonNull Long rideId,
            Authentication authentication
    ) {

        User driver =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "User not found"));
        }

        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "Ride not found"));
        }

        if (!ride.getDriver().getId()
                .equals(driver.getId())) {

            return ResponseEntity.status(403)
                    .body(java.util.Map.of("message", "Only the ride driver can view requests."));
        }

        return ResponseEntity.ok(
                requestRepository
                        .findByRideIdAndStatus(
                                rideId,
                                "PENDING"
                        )
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // DRIVER ACCEPTS / REJECTS
    @PatchMapping("/{requestId}")
    @Transactional
    public ResponseEntity<?> updateRequest(
            @PathVariable @NonNull Long requestId,
            @jakarta.validation.Valid @RequestBody UpdateRideRequestDto request,
            Authentication authentication
    ) {

        User driver =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "User not found"));
        }

        RideRequest rideRequest =
                requestRepository
                        .findByIdForUpdate(requestId)
                        .orElse(null);

        if (rideRequest == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "Ride request not found"));
        }

        Ride ride = rideRepository
                .findByIdForUpdate(rideRequest.getRide().getId())
                .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(java.util.Map.of("message", "Ride not found"));
        }

        if (!ride.getDriver().getId().equals(driver.getId())) {
            log.warn("Ride request response forbidden: requestId={}, rideId={}, authenticatedDriverId={}, actualDriverId={}",
                    requestId, ride.getId(), driver.getId(), ride.getDriver().getId());
            return ResponseEntity.status(403)
                    .body(java.util.Map.of("message", "Only the driver who offered this ride can respond. Please sign in with the ride owner's account."));
        }

        if (!"PENDING".equals(rideRequest.getStatus())) {

            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Request has already been processed."));
        }

        String action =
                request.action()
                        .trim()
                        .toUpperCase();

        if ("ACCEPT".equals(action)) {

            if (!"ACTIVE".equals(ride.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(java.util.Map.of("message", "This ride is no longer accepting passengers."));
            }

            if (ride.getAvailableSeats()
                    < rideRequest.getRequestedSeats()) {

                return ResponseEntity.badRequest()
                        .body(java.util.Map.of("message", "Not enough seats available."));
            }

            int remainingSeats = ride.getAvailableSeats()
                    - rideRequest.getRequestedSeats();

            ride.setAvailableSeats(remainingSeats);

            if (remainingSeats == 0) {
                ride.setStatus("FULL");
            }

            rideRequest.setStatus("ACCEPTED");

            rideRequest.setRespondedAt(
                    LocalDateTime.now()
            );

            rideRepository.save(ride);

        } else if ("REJECT".equals(action)) {

            rideRequest.setStatus("REJECTED");

            rideRequest.setRespondedAt(
                    LocalDateTime.now()
            );

        } else {

            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Action must be ACCEPT or REJECT."));
        }

        RideRequest saved =
                requestRepository.save(rideRequest);

        return ResponseEntity.ok(
                toResponse(saved)
        );
    }

    private RideRequestResponse toResponse(
            RideRequest request
    ) {

        return new RideRequestResponse(
                request.getId(),
                request.getRide().getId(),
                request.getPassenger().getId(),
                request.getPassenger().getName(),
                request.getStatus(),
                request.getRequestedSeats(),
                request.getCreatedAt()
        );
    }
}