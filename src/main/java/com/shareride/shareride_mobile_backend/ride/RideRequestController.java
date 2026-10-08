package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ride-requests")
public class RideRequestController {

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
    public ResponseEntity<?> createRequest(
            @RequestBody CreateRideRequestDto request,
            Authentication authentication
    ) {

        User passenger =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElseThrow();

        Ride ride =
                rideRepository
                        .findById(request.rideId())
                        .orElseThrow();

        if (ride.getDriver().getId()
                .equals(passenger.getId())) {

            return ResponseEntity.badRequest()
                    .body("You cannot request your own ride.");
        }

        int seats =
                request.seats() == null
                        ? 1
                        : request.seats();

        if (seats <= 0) {
            return ResponseEntity.badRequest()
                    .body("Seats must be greater than zero.");
        }

        if (ride.getAvailableSeats() < seats) {
            return ResponseEntity.badRequest()
                    .body("Not enough seats available.");
        }

        if (requestRepository
                .findByRideIdAndPassengerId(
                        ride.getId(),
                        passenger.getId()
                )
                .isPresent()) {

            return ResponseEntity.badRequest()
                    .body("You already requested this ride.");
        }

        RideRequest rideRequest =
                new RideRequest();

        rideRequest.setRide(ride);
        rideRequest.setPassenger(passenger);
        rideRequest.setRequestedSeats(seats);
        rideRequest.setStatus("PENDING");

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
                        .orElseThrow();

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
            @PathVariable Long rideId,
            Authentication authentication
    ) {

        User driver =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElseThrow();

        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElseThrow();

        if (!ride.getDriver().getId()
                .equals(driver.getId())) {

            return ResponseEntity.status(403)
                    .body(
                            "Only the ride driver can view requests."
                    );
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
    public ResponseEntity<?> updateRequest(
            @PathVariable Long requestId,
            @RequestBody UpdateRideRequestDto request,
            Authentication authentication
    ) {

        User driver =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElseThrow();

        RideRequest rideRequest =
                requestRepository
                        .findById(requestId)
                        .orElseThrow();

        Ride ride = rideRequest.getRide();

        if (!ride.getDriver().getId()
                .equals(driver.getId())) {

            return ResponseEntity.status(403)
                    .body("Only the driver can respond.");
        }

        if (!"PENDING".equals(rideRequest.getStatus())) {

            return ResponseEntity.badRequest()
                    .body("Request has already been processed.");
        }

        String action =
                request.action()
                        .trim()
                        .toUpperCase();

        if ("ACCEPT".equals(action)) {

            if (ride.getAvailableSeats()
                    < rideRequest.getRequestedSeats()) {

                return ResponseEntity.badRequest()
                        .body("Not enough seats available.");
            }

            ride.setAvailableSeats(
                    ride.getAvailableSeats()
                            - rideRequest.getRequestedSeats()
            );

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
                    .body(
                            "Action must be ACCEPT or REJECT."
                    );
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