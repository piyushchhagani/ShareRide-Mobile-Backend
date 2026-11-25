package com.shareride.shareride_mobile_backend.payment;

import com.shareride.shareride_mobile_backend.notification.NotificationService;
import com.shareride.shareride_mobile_backend.ride.Ride;
import com.shareride.shareride_mobile_backend.ride.RideRequest;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final FareService fareService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public PaymentController(
            PaymentRepository paymentRepository,
            FareService fareService,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.paymentRepository = paymentRepository;
        this.fareService = fareService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/estimate")
    public ResponseEntity<Map<String, Object>> estimateFare(
            @RequestParam double pickupLat,
            @RequestParam double pickupLon,
            @RequestParam double destLat,
            @RequestParam double destLon
    ) {
        BigDecimal suggested = fareService.suggestFarePerSeat(
                pickupLat, pickupLon, destLat, destLon
        );
        return ResponseEntity.ok(Map.of(
                "suggestedPricePerSeat", suggested,
                "currency", "INR"
        ));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(Authentication authentication) {
        User viewer = getAuthenticatedUser(authentication);
        List<PaymentResponse> responses = paymentRepository.findAllByUserId(viewer.getId())
                .stream()
                .map(payment -> toResponse(payment, viewer.getId()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{paymentId}/mark-paid")
    @Transactional
    public ResponseEntity<?> markPaid(
            @PathVariable @NonNull Long paymentId,
            Authentication authentication
    ) {
        User viewer = getAuthenticatedUser(authentication);
        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Payment record not found"));
        }

        RideRequest req = payment.getRideRequest();
        Ride ride = req.getRide();
        boolean isPassenger = req.getPassenger().getId().equals(viewer.getId());
        boolean isDriver = ride.getDriver().getId().equals(viewer.getId());

        if (!isPassenger && !isDriver) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "You do not have permission to update this payment."));
        }

        if ("CANCELLED".equals(payment.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Cancelled booking payments cannot be marked paid."));
        }

        if ("PAID".equals(payment.getStatus())) {
            return ResponseEntity.ok(toResponse(payment, viewer.getId()));
        }

        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        User otherUser = isPassenger ? ride.getDriver() : req.getPassenger();
        notificationService.createNotification(
                otherUser,
                "PAYMENT_COMPLETED",
                "Payment marked as paid",
                viewer.getName() + " marked ₹" + saved.getAmount() + " as paid for ride #" + ride.getId() + ".",
                ride
        );

        return ResponseEntity.ok(toResponse(saved, viewer.getId()));
    }

    private PaymentResponse toResponse(Payment payment, Long viewerId) {
        RideRequest req = payment.getRideRequest();
        Ride ride = req.getRide();
        boolean isPassenger = req.getPassenger().getId().equals(viewerId);

        return new PaymentResponse(
                payment.getId(),
                req.getId(),
                ride.getId(),
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                req.getPassenger().getId(),
                req.getPassenger().getName(),
                ride.getDriver().getId(),
                ride.getDriver().getName(),
                req.getRequestedSeats(),
                payment.getAmount(),
                payment.getStatus(),
                isPassenger ? "PASSENGER" : "DRIVER",
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
