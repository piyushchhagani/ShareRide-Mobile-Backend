package com.shareride.shareride_mobile_backend.ride;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateRideRequestDto(
        @NotBlank(message = "Action is required")
        @Pattern(regexp = "(?i)^(ACCEPT|REJECT)$", message = "Action must be ACCEPT or REJECT")
        String action
) {}