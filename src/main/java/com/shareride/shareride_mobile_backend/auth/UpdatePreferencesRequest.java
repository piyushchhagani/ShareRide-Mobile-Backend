package com.shareride.shareride_mobile_backend.auth;

public record UpdatePreferencesRequest(
        Boolean rideUpdatesEnabled,
        Boolean bookingUpdatesEnabled,
        Boolean productUpdatesEnabled,
        Boolean locationAssistanceEnabled
) {}
