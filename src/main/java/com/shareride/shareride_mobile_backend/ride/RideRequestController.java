package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.notification.NotificationService;
import com.shareride.shareride_mobile_backend.payment.Payment;
import com.shareride.shareride_mobile_backend.payment.PaymentRepository;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ride-requests")
public class RideRequestController {

    private static final Logger log = LoggerFactory.getLogger(RideRequestController.class);

    private final RideRequestRepository requestRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;

    public RideRequestController(
            RideRequestRepository requestRepository,
            RideRepository rideRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            PaymentRepository paymentRepository
    ) {
        this.requestRepository = requestRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.paymentRepository = paymentRepository;
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
                    .body(Map.of("message", "User not found"));
        }

        Ride ride =
                rideRepository
                        .findByIdForUpdate(request.rideId())
                        .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride not found"));
        }

        if (!"ACTIVE".equals(ride.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "This ride is no longer accepting requests."));
        }

        if (ride.getDriver().getId().equals(passenger.getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "You cannot request your own ride."));
        }

        int seats = request.seats() == null ? 1 : request.seats();

        if (seats <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Seats must be greater than zero."));
        }

        if (ride.getAvailableSeats() < seats) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Not enough seats available."));
        }

        RideRequest existingRequest = requestRepository
                .findByRideIdAndPassengerId(ride.getId(), passenger.getId())
                .orElse(null);

        if (existingRequest != null
                && !"REJECTED".equals(existingRequest.getStatus())
                && !"CANCELLED".equals(existingRequest.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "You already have an active request for this ride."));
        }

        RideRequest rideRequest = existingRequest != null
                ? existingRequest
                : new RideRequest();

        rideRequest.setRide(ride);
        rideRequest.setPassenger(passenger);
        rideRequest.setRequestedSeats(seats);
        rideRequest.setStatus("PENDING");
        rideRequest.setRespondedAt(null);
        rideRequest.setCancelledAt(null);

        RideRequest saved = requestRepository.save(rideRequest);

        if (ride.getDriver().isRideUpdatesEnabled()) {
            notificationService.createNotification(
                    ride.getDriver(),
                    "REQUEST_RECEIVED",
                    "New ride request",
                    passenger.getName() + " requested " + seats + (seats == 1 ? " seat" : " seats") + " on ride #" + ride.getId() + ".",
                    ride
            );
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    // PASSENGER BOOKINGS
    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<?> myRequests(
            @RequestParam(required = false) List<String> status,
            Authentication authentication
    ) {

        User passenger =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (passenger == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "User not found"));
        }

        List<RideRequest> requests;
        if (status != null && !status.isEmpty()) {
            List<String> normalized = status.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase())
                    .toList();
            requests = normalized.isEmpty()
                    ? requestRepository.findByPassengerIdOrderByCreatedAtDesc(passenger.getId())
                    : requestRepository.findByPassengerIdAndStatusInOrderByCreatedAtDesc(passenger.getId(), normalized);
        } else {
            requests = requestRepository.findByPassengerIdOrderByCreatedAtDesc(passenger.getId());
        }

        return ResponseEntity.ok(
                requests.stream().map(this::toResponse).toList()
        );
    }

    // DRIVER SEES REQUESTS
    @GetMapping("/ride/{rideId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> rideRequests(
            @PathVariable @NonNull Long rideId,
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {

        User driver =
                userRepository
                        .findByEmail(authentication.getName())
                        .orElse(null);

        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "User not found"));
        }

        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride not found"));
        }

        if (!ride.getDriver().getId().equals(driver.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Only the ride driver can view requests."));
        }

        List<RideRequest> items = (status != null && !status.isBlank())
                ? requestRepository.findByRideIdAndStatusOrderByCreatedAtDesc(rideId, status.trim().toUpperCase())
                : requestRepository.findByRideIdOrderByCreatedAtDesc(rideId);

        return ResponseEntity.ok(items.stream().map(this::toResponse).toList());
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
                    .body(Map.of("message", "User not found"));
        }

        RideRequest rideRequest =
                requestRepository
                        .findByIdForUpdate(requestId)
                        .orElse(null);

        if (rideRequest == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride request not found"));
        }

        Ride ride = rideRepository
                .findByIdForUpdate(rideRequest.getRide().getId())
                .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride not found"));
        }

        if (!ride.getDriver().getId().equals(driver.getId())) {
            log.warn("Ride request response forbidden: requestId={}, rideId={}, authenticatedDriverId={}, actualDriverId={}",
                    requestId, ride.getId(), driver.getId(), ride.getDriver().getId());
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Only the driver who offered this ride can respond. Please sign in with the ride owner's account."));
        }

        if (!"PENDING".equals(rideRequest.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Request has already been processed."));
        }

        String action = request.action().trim().toUpperCase();

        if ("ACCEPT".equals(action)) {
            if (!"ACTIVE".equals(ride.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "This ride is no longer accepting passengers."));
            }

            if (ride.getAvailableSeats() < rideRequest.getRequestedSeats()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Not enough seats available."));
            }

            int remainingSeats = ride.getAvailableSeats() - rideRequest.getRequestedSeats();
            ride.setAvailableSeats(remainingSeats);

            if (remainingSeats == 0) {
                ride.setStatus("FULL");
            }
            ride.setUpdatedAt(LocalDateTime.now());

            rideRequest.setStatus("ACCEPTED");
            rideRequest.setRespondedAt(LocalDateTime.now());

            rideRepository.save(ride);

        } else if ("REJECT".equals(action)) {
            rideRequest.setStatus("REJECTED");
            rideRequest.setRespondedAt(LocalDateTime.now());
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Action must be ACCEPT or REJECT."));
        }

        RideRequest saved = requestRepository.save(rideRequest);

        if ("ACCEPT".equals(action)) {
            BigDecimal seatPrice = ride.getPricePerSeat() != null ? ride.getPricePerSeat() : BigDecimal.ZERO;
            BigDecimal totalAmount = seatPrice.multiply(BigDecimal.valueOf(saved.getRequestedSeats()));
            Payment payment = paymentRepository.findByRideRequestId(saved.getId()).orElseGet(Payment::new);
            payment.setRideRequest(saved);
            payment.setAmount(totalAmount);
            payment.setStatus("UNPAID");
            payment.setPaidAt(null);
            paymentRepository.save(payment);
        }

        if (rideRequest.getPassenger().isBookingUpdatesEnabled()) {
            if ("ACCEPT".equals(action)) {
                notificationService.createNotification(
                        rideRequest.getPassenger(),
                        "REQUEST_ACCEPTED",
                        "Booking confirmed",
                        driver.getName() + " accepted your request for ride #" + ride.getId() + ".",
                        ride
                );
            } else {
                notificationService.createNotification(
                        rideRequest.getPassenger(),
                        "REQUEST_REJECTED",
                        "Request declined",
                        driver.getName() + " declined your request for ride #" + ride.getId() + ".",
                        ride
                );
            }
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    // PASSENGER CANCELS REQUEST / BOOKING
    @PatchMapping("/{requestId}/cancel")
    @Transactional
    public ResponseEntity<?> cancelRequestPatch(
            @PathVariable @NonNull Long requestId,
            Authentication authentication
    ) {
        return cancelRequestInternal(requestId, authentication);
    }

    @DeleteMapping("/{requestId}")
    @Transactional
    public ResponseEntity<?> cancelRequestDelete(
            @PathVariable @NonNull Long requestId,
            Authentication authentication
    ) {
        return cancelRequestInternal(requestId, authentication);
    }

    private ResponseEntity<?> cancelRequestInternal(
            Long requestId,
            Authentication authentication
    ) {
        User passenger = userRepository
                .findByEmail(authentication.getName())
                .orElse(null);

        if (passenger == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "User not found"));
        }

        RideRequest rideRequest = requestRepository
                .findByIdForUpdate(requestId)
                .orElse(null);

        if (rideRequest == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride request not found"));
        }

        if (!rideRequest.getPassenger().getId().equals(passenger.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "You can only cancel your own ride requests."));
        }

        String currentStatus = rideRequest.getStatus();
        if ("CANCELLED".equals(currentStatus)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "This request is already cancelled."));
        }
        if ("REJECTED".equals(currentStatus)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Rejected requests cannot be cancelled."));
        }

        Ride ride = rideRepository
                .findByIdForUpdate(rideRequest.getRide().getId())
                .orElse(null);

        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride not found"));
        }

        if ("COMPLETED".equals(ride.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Completed rides cannot be cancelled."));
        }

        if ("ACCEPTED".equals(currentStatus)) {
            ride.setAvailableSeats(ride.getAvailableSeats() + rideRequest.getRequestedSeats());
            if ("FULL".equals(ride.getStatus()) && ride.getAvailableSeats() > 0) {
                ride.setStatus("ACTIVE");
            }
            ride.setUpdatedAt(LocalDateTime.now());
            rideRepository.save(ride);
        }

        rideRequest.setStatus("CANCELLED");
        rideRequest.setCancelledAt(LocalDateTime.now());

        RideRequest saved = requestRepository.save(rideRequest);

        paymentRepository.findByRideRequestId(saved.getId()).ifPresent(payment -> {
            if ("UNPAID".equals(payment.getStatus())) {
                payment.setStatus("CANCELLED");
                paymentRepository.save(payment);
            }
        });

        log.info("Ride request cancelled: requestId={}, passengerId={}, restoredSeats={}",
                requestId, passenger.getId(), "ACCEPTED".equals(currentStatus));

        if (ride.getDriver().isRideUpdatesEnabled()) {
            notificationService.createNotification(
                    ride.getDriver(),
                    "REQUEST_CANCELLED",
                    "Passenger cancelled",
                    passenger.getName() + " cancelled their request for ride #" + ride.getId() + ".",
                    ride
            );
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    private RideRequestResponse toResponse(RideRequest request) {
        Ride ride = request.getRide();
        return new RideRequestResponse(
                request.getId(),
                ride.getId(),
                request.getPassenger().getId(),
                request.getPassenger().getName(),
                request.getStatus(),
                request.getRequestedSeats(),
                request.getCreatedAt(),
                request.getCancelledAt(),
                ride.getDriver().getId(),
                ride.getDriver().getName(),
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                ride.getDepartureTime(),
                ride.getStatus()
        );
    }
}
