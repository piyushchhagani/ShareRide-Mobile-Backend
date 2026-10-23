package com.shareride.shareride_mobile_backend.route;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> calculateRoute(
            @Valid @RequestBody RouteRequest request
    ) {
        RouteService.RouteResult result = routeService.calculateRoute(
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.destinationLatitude(),
                request.destinationLongitude()
        );

        return ResponseEntity.ok(new RouteResponse(
                result.getDistanceMeters(),
                result.getDurationSeconds(),
                result.getPoints().stream()
                        .map(point -> List.of(point.getLongitude(), point.getLatitude()))
                        .toList(),
                result.isApproximate()
        ));
    }
}

