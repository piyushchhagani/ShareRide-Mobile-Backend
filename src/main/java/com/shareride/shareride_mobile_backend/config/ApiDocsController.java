package com.shareride.shareride_mobile_backend.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class ApiDocsController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getApiOverview() {
        return ResponseEntity.ok(Map.of(
                "service", "ShareRide Mobile Backend",
                "version", "1.0.0",
                "endpoints", List.of(
                        Map.of("method", "POST", "path", "/api/auth/register", "auth", false, "summary", "Register student or faculty account"),
                        Map.of("method", "POST", "path", "/api/auth/login", "auth", false, "summary", "Authenticate and obtain JWT token"),
                        Map.of("method", "POST", "path", "/api/auth/forgot-password", "auth", false, "summary", "Generate 6-digit password reset code"),
                        Map.of("method", "POST", "path", "/api/auth/reset-password", "auth", false, "summary", "Reset password using code"),
                        Map.of("method", "GET", "path", "/api/auth/me", "auth", true, "summary", "Get current user profile and preferences"),
                        Map.of("method", "PUT", "path", "/api/auth/profile", "auth", true, "summary", "Update name, phone, and avatarUrl"),
                        Map.of("method", "PUT", "path", "/api/auth/preferences", "auth", true, "summary", "Update notification and location preferences"),
                        Map.of("method", "PUT", "path", "/api/auth/password", "auth", true, "summary", "Change current password"),
                        Map.of("method", "POST", "path", "/api/rides", "auth", true, "summary", "Offer a new ride"),
                        Map.of("method", "GET", "path", "/api/rides", "auth", true, "summary", "List active rides"),
                        Map.of("method", "GET", "path", "/api/rides/mine", "auth", true, "summary", "List driver's rides (supports ?status=)"),
                        Map.of("method", "POST", "path", "/api/rides/find", "auth", true, "summary", "Find matching rides with route scoring, filters, sorting, and pagination"),
                        Map.of("method", "PATCH", "path", "/api/rides/{rideId}/status", "auth", true, "summary", "Update ride status (ACTIVE/COMPLETED/CANCELLED)"),
                        Map.of("method", "POST", "path", "/api/ride-requests", "auth", true, "summary", "Request seats on a ride"),
                        Map.of("method", "GET", "path", "/api/ride-requests/mine", "auth", true, "summary", "List passenger's bookings"),
                        Map.of("method", "GET", "path", "/api/ride-requests/ride/{rideId}", "auth", true, "summary", "List requests for a driver's ride"),
                        Map.of("method", "PATCH", "path", "/api/ride-requests/{requestId}", "auth", true, "summary", "Accept or reject a ride request"),
                        Map.of("method", "PATCH", "path", "/api/ride-requests/{requestId}/cancel", "auth", true, "summary", "Cancel passenger request and restore seats"),
                        Map.of("method", "GET", "path", "/api/notifications", "auth", true, "summary", "List user notifications"),
                        Map.of("method", "PATCH", "path", "/api/notifications/{id}/read", "auth", true, "summary", "Mark notification as read"),
                        Map.of("method", "PATCH", "path", "/api/notifications/read-all", "auth", true, "summary", "Mark all notifications as read"),
                        Map.of("method", "POST", "path", "/api/ratings", "auth", true, "summary", "Submit 1-5 star rating for completed ride"),
                        Map.of("method", "GET", "path", "/api/ratings/mine", "auth", true, "summary", "Get current user's ratings summary"),
                        Map.of("method", "GET", "path", "/api/ratings/user/{userId}", "auth", true, "summary", "Get ratings summary for a user"),
                        Map.of("method", "GET", "path", "/api/payments/estimate", "auth", true, "summary", "Estimate suggested fare per seat"),
                        Map.of("method", "GET", "path", "/api/payments/history", "auth", true, "summary", "Get user's payment ledger"),
                        Map.of("method", "PATCH", "path", "/api/payments/{paymentId}/mark-paid", "auth", true, "summary", "Mark ride payment as settled")
                )
        ));
    }
}
