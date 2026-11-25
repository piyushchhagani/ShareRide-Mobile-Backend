package com.shareride.shareride_mobile_backend.auth;

import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.dev.expose-reset-token:false}")
    private boolean exposeResetToken;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email already registered"));
        }

        User user = new User();

        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(
                passwordEncoder.encode(request.password())
        );
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        if (request.role() != null && !request.role().isBlank()) {
            user.setRole(request.role().trim().toUpperCase());
        }

        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "User registered successfully")
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ) {

        User user = userRepository
                .findByEmail(request.email().trim().toLowerCase())
                .orElse(null);

        if (user == null ||
                !passwordEncoder.matches(
                        request.password(),
                        user.getPassword()
                )) {

            return ResponseEntity
                    .status(401)
                    .body(Map.of("message", "Invalid email or password"));
        }

        String token =
                jwtService.generateToken(user.getEmail());

        return ResponseEntity.ok(
                new LoginResponse(
                        token,
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(toUserResponse(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        user.setName(request.name().trim());
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        user.setAvatarUrl(request.avatarUrl() != null && !request.avatarUrl().isBlank() ? request.avatarUrl().trim() : null);
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        log.info("Profile updated: userId={}", saved.getId());

        return ResponseEntity.ok(toUserResponse(saved));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserResponse> updatePreferences(
            @RequestBody UpdatePreferencesRequest request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        if (request.rideUpdatesEnabled() != null) {
            user.setRideUpdatesEnabled(request.rideUpdatesEnabled());
        }
        if (request.bookingUpdatesEnabled() != null) {
            user.setBookingUpdatesEnabled(request.bookingUpdatesEnabled());
        }
        if (request.productUpdatesEnabled() != null) {
            user.setProductUpdatesEnabled(request.productUpdatesEnabled());
        }
        if (request.locationAssistanceEnabled() != null) {
            user.setLocationAssistanceEnabled(request.locationAssistanceEnabled());
        }
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserResponse(saved));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Current password is incorrect"));
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "New password must be different from your current password"));
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Password changed: userId={}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "If an account exists for this email, a reset code has been generated.");

        if (user != null) {
            String token = String.format("%06d", RANDOM.nextInt(1_000_000));
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Password reset code generated for userId={}", user.getId());
            if (exposeResetToken) {
                response.put("devResetToken", token);
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        User user = userRepository.findByResetToken(request.token().trim()).orElse(null);

        if (user == null || user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid or expired reset code"));
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Password reset completed: userId={}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.isRideUpdatesEnabled(),
                user.isBookingUpdatesEnabled(),
                user.isProductUpdatesEnabled(),
                user.isLocationAssistanceEnabled()
        );
    }
}
