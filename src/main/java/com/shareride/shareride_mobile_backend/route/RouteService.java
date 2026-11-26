package com.shareride.shareride_mobile_backend.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RouteService {

    private static final Logger log =
            LoggerFactory.getLogger(RouteService.class);

    private static final String OSRM_BASE_URL = "https://router.project-osrm.org";

    private static final Pattern COORDINATE_PATTERN = Pattern.compile(
            "\\[\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*,\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*\\]"
    );

    private final RestTemplate restTemplate;

    @Value("${ors.api.key}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    public RouteService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ORS_API_KEY is not configured.");
        }
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
    @SuppressWarnings("null")
    public RouteResult calculateRoute(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        if (!isPlaceholderApiKey(apiKey)) {
            try {
                return fetchFromOrs(startLatitude, startLongitude, endLatitude, endLongitude);
            } catch (Exception exception) {
                log.warn("OpenRouteService request failed: {}. Trying OSRM fallback.", exception.getMessage());
            }
        }

        try {
            return fetchFromOsrm(startLatitude, startLongitude, endLatitude, endLongitude);
        } catch (Exception exception) {
            log.warn("OSRM request failed: {}. Using straight-line fallback.", exception.getMessage());
        }

        return buildStraightLineFallback(startLatitude, startLongitude, endLatitude, endLongitude);
    }

    private boolean isPlaceholderApiKey(String key) {
        return key == null || key.isBlank() || key.startsWith("local-dev") || key.startsWith("REPLACE_");
    }

    private RouteResult fetchFromOrs(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        String url = baseUrl + "/v2/directions/driving-car/geojson";

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

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        return parseGeoJsonRoute(response, "OpenRouteService");
    }

    private RouteResult fetchFromOsrm(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        String url = String.format(
                Locale.US,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                OSRM_BASE_URL,
                startLongitude,
                startLatitude,
                endLongitude,
                endLatitude
        );

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return parseGeoJsonRoute(response, "OSRM");
    }

    private RouteResult parseGeoJsonRoute(ResponseEntity<String> response, String provider) {
        if (!response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null
                || response.getBody().isBlank()) {
            throw new RuntimeException("Invalid routing response from " + provider);
        }

        String responseBody = response.getBody();
        double distanceMeters = extractNumberAfterKey(responseBody, "distance");
        double durationSeconds = extractNumberAfterKey(responseBody, "duration");
        List<RoutePoint> routePoints = extractRoutePoints(responseBody);

        if (routePoints.isEmpty()) {
            throw new RuntimeException(provider + " returned no route coordinates");
        }

        return new RouteResult(distanceMeters, durationSeconds, routePoints, false);
    }

    private RouteResult buildStraightLineFallback(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        /*
         * Keep the application usable even if routing providers are temporarily
         * unavailable. The matching service can use this fallback route.
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
        double durationSeconds = (distanceMeters / 1000.0) / 30.0 * 3600.0;

        List<RoutePoint> fallbackPoints = new ArrayList<>();
        fallbackPoints.add(new RoutePoint(startLatitude, startLongitude));
        fallbackPoints.add(new RoutePoint(endLatitude, endLongitude));

        return new RouteResult(distanceMeters, durationSeconds, fallbackPoints, true);
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
        private final boolean approximate;

        public RouteResult(
                double distanceMeters,
                double durationSeconds,
                List<RoutePoint> points,
                boolean approximate
        ) {
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
            this.points = points;
            this.approximate = approximate;
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

        public boolean isApproximate() {
            return approximate;
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
