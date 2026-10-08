package com.shareride.shareride_mobile_backend.route;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> calculateRoute(
            @RequestBody RouteRequest request
    ) {
        return ResponseEntity.ok(
                routeService.calculateRoute(request)
        );
    }
}