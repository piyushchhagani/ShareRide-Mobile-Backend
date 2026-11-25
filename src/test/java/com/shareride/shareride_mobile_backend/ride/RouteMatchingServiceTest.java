package com.shareride.shareride_mobile_backend.ride;

import com.shareride.shareride_mobile_backend.route.RouteResponse;
import com.shareride.shareride_mobile_backend.route.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;

class RouteMatchingServiceTest {

    @Test
    void identicalRouteAndTimeScoresNear100() {
        RouteService routeService = Mockito.mock(RouteService.class);
        List<RouteService.RoutePoint> points = List.of(
                new RouteService.RoutePoint(21.1458, 79.0882),
                new RouteService.RoutePoint(21.1500, 79.0950),
                new RouteService.RoutePoint(21.1550, 79.1000)
        );
        Mockito.when(routeService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteService.RouteResult(2500.0, 300.0, points, false));

        RouteMatchingService matchingService = new RouteMatchingService(routeService);

        LocalDateTime departure = LocalDateTime.now().plusHours(2);
        Ride ride = new Ride();
        ride.setPickupLatitude(21.1458);
        ride.setPickupLongitude(79.0882);
        ride.setDestinationLatitude(21.1550);
        ride.setDestinationLongitude(79.1000);
        ride.setDepartureTime(departure);

        RouteResponse passengerRoute = new RouteResponse(
                2500.0,
                300.0,
                List.of(
                        List.of(79.0882, 21.1458),
                        List.of(79.0950, 21.1500),
                        List.of(79.1000, 21.1550)
                )
        );

        MatchScore score = matchingService.calculate(ride, passengerRoute, departure);
        assertEquals(100.0, score.totalScore());
        assertEquals(100.0, score.routeScore());
        assertEquals(100.0, score.timeScore());
    }

    @Test
    void distantRouteHasLowScore() {
        RouteService routeService = Mockito.mock(RouteService.class);
        Mockito.when(routeService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("offline"));

        RouteMatchingService matchingService = new RouteMatchingService(routeService);

        LocalDateTime departure = LocalDateTime.now().plusHours(2);
        Ride ride = new Ride();
        ride.setPickupLatitude(21.1458);
        ride.setPickupLongitude(79.0882);
        ride.setDestinationLatitude(21.1550);
        ride.setDestinationLongitude(79.1000);
        ride.setDepartureTime(departure.plusMinutes(50));

        RouteResponse passengerRoute = new RouteResponse(
                5000.0,
                600.0,
                List.of(
                        List.of(72.8777, 19.0760),
                        List.of(72.9000, 19.1000)
                )
        );

        MatchScore score = matchingService.calculate(ride, passengerRoute, departure);
        assertEquals(0.0, score.routeScore());
        assertTrue(score.totalScore() < 25.0);
    }

    @Test
    void partialCorridorMatchesBidirectionally() {
        RouteService routeService = Mockito.mock(RouteService.class);
        Mockito.when(routeService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("offline"));

        RouteMatchingService matchingService = new RouteMatchingService(routeService);

        LocalDateTime departure = LocalDateTime.now().plusHours(2);
        // Short driver leg along the corridor (~0.3 km from passenger straight-line path)
        Ride ride = new Ride();
        ride.setPickupLatitude(21.1500);
        ride.setPickupLongitude(79.0670);
        ride.setDestinationLatitude(21.1200);
        ride.setDestinationLongitude(79.0660);
        ride.setDepartureTime(departure);

        // Longer passenger search along same corridor: Gittikhadan -> Airport
        RouteResponse passengerRoute = new RouteResponse(
                13500.0,
                1600.0,
                List.of(
                        List.of(79.0650, 21.1750),
                        List.of(79.0640, 21.0860)
                )
        );

        MatchScore score = matchingService.calculate(ride, passengerRoute, departure);
        assertTrue(score.routeScore() >= 50.0, "Expected corridor routeScore >= 50, got " + score.routeScore());
        assertTrue(score.totalScore() >= 25.0, "Expected totalScore >= 25, got " + score.totalScore());
    }
}
