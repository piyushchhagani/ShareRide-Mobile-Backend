package com.shareride.shareride_mobile_backend.ride;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateRideStatusRequest(
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "(?i)^(ACTIVE|COMPLETED|CANCELLED)$", message = "Status must be ACTIVE, COMPLETED, or CANCELLED")
        String status
) {}
