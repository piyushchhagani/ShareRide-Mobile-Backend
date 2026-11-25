package com.shareride.shareride_mobile_backend.rating;

import com.shareride.shareride_mobile_backend.notification.NotificationService;
import com.shareride.shareride_mobile_backend.ride.Ride;
import com.shareride.shareride_mobile_backend.ride.RideRepository;
import com.shareride.shareride_mobile_backend.ride.RideRequest;
import com.shareride.shareride_mobile_backend.ride.RideRequestRepository;
import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingRepository ratingRepository;
    private final RideRepository rideRepository;
    private final RideRequestRepository rideRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public RatingController(
            RatingRepository ratingRepository,
            RideRepository rideRepository,
            RideRequestRepository rideRequestRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.ratingRepository = ratingRepository;
        this.rideRepository = rideRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createRating(
            @Valid @RequestBody CreateRatingRequest request,
            Authentication authentication
    ) {
        User rater = getAuthenticatedUser(authentication);

        if (rater.getId().equals(request.ratedUserId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "You cannot rate yourself."));
        }

        Ride ride = rideRepository.findById(request.rideId()).orElse(null);
        if (ride == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Ride not found"));
        }

        if (!"COMPLETED".equals(ride.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Ratings can only be submitted after a ride is completed."));
        }

        User ratedUser = userRepository.findById(request.ratedUserId()).orElse(null);
        if (ratedUser == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Rated user not found"));
        }

        boolean raterIsDriver = ride.getDriver().getId().equals(rater.getId());
        boolean ratedIsDriver = ride.getDriver().getId().equals(ratedUser.getId());

        if (raterIsDriver) {
            RideRequest passengerReq = rideRequestRepository
                    .findByRideIdAndPassengerId(ride.getId(), ratedUser.getId())
                    .orElse(null);
            if (passengerReq == null || !"ACCEPTED".equals(passengerReq.getStatus())) {
                return ResponseEntity.status(403)
                        .body(Map.of("message", "You can only rate passengers whose request was accepted for this ride."));
            }
        } else if (ratedIsDriver) {
            RideRequest passengerReq = rideRequestRepository
                    .findByRideIdAndPassengerId(ride.getId(), rater.getId())
                    .orElse(null);
            if (passengerReq == null || !"ACCEPTED".equals(passengerReq.getStatus())) {
                return ResponseEntity.status(403)
                        .body(Map.of("message", "You can only rate the driver if your booking was accepted."));
            }
        } else {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "You can only rate participants from your completed ride."));
        }

        if (ratingRepository.existsByRideIdAndRaterIdAndRatedUserId(
                ride.getId(), rater.getId(), ratedUser.getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "You have already rated this user for this ride."));
        }

        Rating rating = new Rating();
        rating.setRide(ride);
        rating.setRater(rater);
        rating.setRatedUser(ratedUser);
        rating.setScore(request.score());
        rating.setComment(request.comment() != null && !request.comment().isBlank()
                ? request.comment().trim()
                : null);
        rating.setCreatedAt(LocalDateTime.now());

        Rating saved = ratingRepository.save(rating);

        notificationService.createNotification(
                ratedUser,
                "RATING_RECEIVED",
                "New rating received",
                rater.getName() + " rated you " + request.score() + "/5 for ride #" + ride.getId() + ".",
                ride
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/user/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getUserRatings(@PathVariable @NonNull Long userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }

        return ResponseEntity.ok(buildUserSummary(userId));
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<UserRatingSummaryResponse> getMyRatings(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(buildUserSummary(user.getId()));
    }

    @GetMapping("/ride/{rideId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RatingResponse>> getRideRatings(@PathVariable @NonNull Long rideId) {
        List<RatingResponse> ratings = ratingRepository.findByRideIdOrderByCreatedAtDesc(rideId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ratings);
    }

    private UserRatingSummaryResponse buildUserSummary(Long userId) {
        Double avg = ratingRepository.findAverageScoreByRatedUserId(userId);
        double roundedAvg = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
        long totalRatings = ratingRepository.countByRatedUserId(userId);
        long completedAsDriver = rideRepository.countByDriverIdAndStatus(userId, "COMPLETED");
        long completedAsPassenger = rideRequestRepository.countCompletedByPassengerId(userId);
        List<RatingResponse> ratings = ratingRepository.findByRatedUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        return new UserRatingSummaryResponse(
                userId,
                totalRatings > 0 ? roundedAvg : null,
                totalRatings,
                completedAsDriver + completedAsPassenger,
                ratings
        );
    }

    private RatingResponse toResponse(Rating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getRide().getId(),
                rating.getRater().getId(),
                rating.getRater().getName(),
                rating.getRatedUser().getId(),
                rating.getRatedUser().getName(),
                rating.getScore(),
                rating.getComment(),
                rating.getCreatedAt()
        );
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
