package com.shareride.shareride_mobile_backend.route;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteService.RouteResult> calculateRoute(
            @RequestBody RouteRequest request
    ) {
        double startLatitude = resolveCoordinate(request, "startLatitude", "getStartLatitude");
        double startLongitude = resolveCoordinate(request, "startLongitude", "getStartLongitude");
        double endLatitude = resolveCoordinate(request, "endLatitude", "endLat", "getEndLatitude", "getEndLat");
        double endLongitude = resolveCoordinate(request, "endLongitude", "endLng", "getEndLongitude", "getEndLng");

        return ResponseEntity.ok(
                routeService.calculateRoute(
                        startLatitude,
                        startLongitude,
                        endLatitude,
                        endLongitude
                )
        );
    }

    private double resolveCoordinate(RouteRequest request, String... candidateMethods) {
        for (String candidate : candidateMethods) {
            Method method = findMethod(request, candidate);
            if (method != null) {
                try {
                    Object value = method.invoke(request);
                    if (value != null) {
                        return ((Number) value).doubleValue();
                    }
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                    // Try the next possible accessor name.
                }
            }
        }

        throw new IllegalArgumentException("Missing required coordinate data in route request");
    }

    private Method findMethod(RouteRequest request, String methodName) {
        try {
            return request.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            String beanGetter = "get" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
            try {
                return request.getClass().getMethod(beanGetter);
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }
}