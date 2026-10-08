package com.shareride.shareride_mobile_backend.ride;

public record MatchScore(
        double totalScore,
        double routeScore,
        double timeScore,
        double pickupScore,
        double destinationScore,
        double detourScore
) {
}