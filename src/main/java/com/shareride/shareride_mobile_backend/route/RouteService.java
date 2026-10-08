package com.shareride.shareride_mobile_backend.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RouteService {

    private static final Pattern COORDINATE_PATTERN = Pattern.compile(
            "\\[\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*,\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*\\]"
    );

    private final RestTemplate restTemplate;

    @Value("${ors.api.key}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    public RouteService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Calculates a route between two coordinates using OpenRouteService.
     *
     * Coordinates are supplied as:
     * [latitude, longitude]
     *
     * OpenRouteService expects:
     * [longitude, latitude]
     */
    public RouteResult calculateRoute(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {

        String url = baseUrl
                + "/openrouteservice/v2/directions/driving-car/geojson";

        try {
            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(
                    MediaType.valueOf("application/geo+json"),
                    MediaType.APPLICATION_JSON
            ));

            headers.set("Authorization", apiKey);

            Map<String, Object> body = new HashMap<>();

            List<List<Double>> coordinates = new ArrayList<>();

            // ORS requires [longitude, latitude]
            coordinates.add(List.of(startLongitude, startLatitude));
            coordinates.add(List.of(endLongitude, endLatitude));

            body.put("coordinates", coordinates);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || response.getBody().isBlank()) {

                throw new RuntimeException(
                        "Invalid routing response from OpenRouteService"
                );
            }

            double distanceMeters =
                    extractNumberAfterKey(response.getBody(), "distance");

            double durationSeconds =
                    extractNumberAfterKey(response.getBody(), "duration");

            List<RoutePoint> routePoints = extractRoutePoints(
                    response.getBody()
            );

            if (routePoints.isEmpty()) {
                throw new RuntimeException(
                        "OpenRouteService returned no route coordinates"
                );
            }

            return new RouteResult(
                    distanceMeters,
                    durationSeconds,
                    routePoints
            );

        } catch (Exception exception) {

            System.err.println(
                    "OpenRouteService request failed: "
                            + exception.getMessage()
            );

            /*
             * Keep the application usable even if ORS is temporarily
             * unavailable. The matching service can use this fallback
             * route information.
             */
            double distanceMeters = calculateStraightLineDistance(
                    startLatitude,
                    startLongitude,
                    endLatitude,
                    endLongitude
            );

            /*
             * Fallback assumption:
             * average driving speed = 30 km/h
             */
            double durationSeconds =
                    (distanceMeters / 1000.0) / 30.0 * 3600.0;

            List<RoutePoint> fallbackPoints = new ArrayList<>();

            fallbackPoints.add(
                    new RoutePoint(
                            startLatitude,
                            startLongitude
                    )
            );

            fallbackPoints.add(
                    new RoutePoint(
                            endLatitude,
                            endLongitude
                    )
            );

            return new RouteResult(
                    distanceMeters,
                    durationSeconds,
                    fallbackPoints
            );
        }
    }

    /**
     * Calculates straight-line distance using the Haversine formula.
     *
     * @return distance in meters
     */
    private double calculateStraightLineDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double EARTH_RADIUS_METERS = 6_371_000.0;

        double lat1Radians = Math.toRadians(latitude1);
        double lat2Radians = Math.toRadians(latitude2);

        double deltaLatitude =
                Math.toRadians(latitude2 - latitude1);

        double deltaLongitude =
                Math.toRadians(longitude2 - longitude1);

        double a =
                Math.sin(deltaLatitude / 2)
                        * Math.sin(deltaLatitude / 2)
                        + Math.cos(lat1Radians)
                        * Math.cos(lat2Radians)
                        * Math.sin(deltaLongitude / 2)
                        * Math.sin(deltaLongitude / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_METERS * c;
    }

    private static double extractNumberAfterKey(String body, String key) {
        int keyIndex = body.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return 0.0;
        }

        int valueIndex = body.indexOf(':', keyIndex);
        if (valueIndex < 0) {
            return 0.0;
        }

        int start = valueIndex + 1;
        while (start < body.length()
                && Character.isWhitespace(body.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < body.length()) {
            char current = body.charAt(end);
            if (Character.isDigit(current)
                    || current == '.'
                    || current == '-'
                    || current == '+'
                    || current == 'e'
                    || current == 'E') {
                end++;
            } else {
                break;
            }
        }

        if (end == start) {
            return 0.0;
        }

        return Double.parseDouble(body.substring(start, end));
    }

    private static List<RoutePoint> extractRoutePoints(String body) {
        int geometryIndex = body.indexOf("\"geometry\"");
        if (geometryIndex < 0) {
            return List.of();
        }

        int coordinatesIndex = body.indexOf("\"coordinates\"", geometryIndex);
        if (coordinatesIndex < 0) {
            return List.of();
        }

        int start = body.indexOf('[', coordinatesIndex);
        if (start < 0) {
            return List.of();
        }

        int end = findMatchingBracket(body, start);
        if (end < 0) {
            return List.of();
        }

        String coordinatesBlock = body.substring(start, end + 1);
        Matcher matcher = COORDINATE_PATTERN.matcher(coordinatesBlock);

        List<RoutePoint> routePoints = new ArrayList<>();
        while (matcher.find()) {
            double longitude = Double.parseDouble(matcher.group(1));
            double latitude = Double.parseDouble(matcher.group(2));
            routePoints.add(new RoutePoint(latitude, longitude));
        }

        return routePoints;
    }

    private static int findMatchingBracket(String body, int startIndex) {
        int depth = 0;

        for (int index = startIndex; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    /**
     * Represents a calculated route.
     */
    public static class RouteResult {

        private final double distanceMeters;
        private final double durationSeconds;
        private final List<RoutePoint> points;

        public RouteResult(
                double distanceMeters,
                double durationSeconds,
                List<RoutePoint> points
        ) {
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
            this.points = points;
        }

        public double getDistanceMeters() {
            return distanceMeters;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public List<RoutePoint> getPoints() {
            return points;
        }
    }

    /**
     * A point belonging to the calculated route.
     */
    public static class RoutePoint {

        private final double latitude;
        private final double longitude;

        public RoutePoint(
                double latitude,
                double longitude
        ) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}