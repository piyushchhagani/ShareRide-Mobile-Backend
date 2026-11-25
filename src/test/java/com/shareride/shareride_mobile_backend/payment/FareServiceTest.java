package com.shareride.shareride_mobile_backend.payment;

import com.shareride.shareride_mobile_backend.route.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;

class FareServiceTest {

    @Test
    void calculatesMinimumFareForShortTrips() {
        RouteService routeService = Mockito.mock(RouteService.class);
        Mockito.when(routeService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteService.RouteResult(
                        500.0,
                        60.0,
                        List.of(new RouteService.RoutePoint(21.14, 79.08)),
                        false
                ));

        FareService fareService = new FareService(routeService);
        BigDecimal fare = fareService.suggestFarePerSeat(21.14, 79.08, 21.142, 79.082);
        assertEquals(new BigDecimal("20.00"), fare);
    }

    @Test
    void calculatesDistanceBasedFareRoundedToFive() {
        RouteService routeService = Mockito.mock(RouteService.class);
        Mockito.when(routeService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteService.RouteResult(
                        10_000.0,
                        900.0,
                        List.of(new RouteService.RoutePoint(21.14, 79.08)),
                        false
                ));

        FareService fareService = new FareService(routeService);
        // 15 + 10 * 6 = 75.00
        BigDecimal fare = fareService.suggestFarePerSeat(21.14, 79.08, 21.20, 79.15);
        assertEquals(new BigDecimal("75.00"), fare);
    }
}
