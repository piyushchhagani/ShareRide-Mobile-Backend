package com.shareride.shareride_mobile_backend.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    private static final Logger log =
            LoggerFactory.getLogger(RouteService.class);

    private final RestTemplate restTemplate;

    @Value("${ors.api.key}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    public RouteService(
            RestTemplate restTemplate
    ) {
        this.restTemplate = restTemplate;
    }

    public RouteResponse calculateRoute(RouteRequest request) {

        /*
         * Keep ors.base-url as:
         *
         * https://api.heigit.org
         *
         * The openrouteservice path is added here.
         */
        String url =
                baseUrl
                        + "/openrouteservice/v2/directions/driving-car/geojson";

        log.info(
                "Requesting route from ORS: {} -> {}",
                request.pickupLatitude()
                        + "," +
                        request.pickupLongitude(),
                request.destinationLatitude()
                        + "," +
                        request.destinationLongitude()
        );

        try {

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            /*
             * Request GeoJSON response.
             */
            headers.setAccept(
                    List.of(
                            MediaType.valueOf("application/geo+json"),
                            MediaType.APPLICATION_JSON
                    )
            );

            /*
             * ORS / HeiGIT API key.
             */
            headers.set(
                    "Authorization",
                    apiKey
            );

            String body = String.format(
                    "{\n"
                            + "  \"coordinates\": [\n"
                            + "    [%s, %s],\n"
                            + "    [%s, %s]\n"
                            + "  ]\n"
                            + "}",
                    request.pickupLongitude(),
                    request.pickupLatitude(),
                    request.destinationLongitude(),
                    request.destinationLatitude()
            );

            HttpEntity<String> entity =
                    new HttpEntity<>(
                            body,
                            headers
                    );

            log.debug(
                    "ORS request URL: {}",
                    url
            );

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            String responseBody =
                    response.getBody();

            if (responseBody == null ||
                    responseBody.isBlank()) {

                throw new IllegalStateException(
                        "Routing service returned an empty response."
                );
            }

            log.info(
                    "ORS response received: status={}",
                    response.getStatusCode()
            );

            return parseGeoJson(responseBody);

        } catch (HttpStatusCodeException exception) {

            log.error(
                    "Routing service rejected request: status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );

            throw new IllegalStateException(
                    "Route service failed: "
                            + exception.getStatusCode(),
                    exception
            );

        } catch (Exception exception) {

            log.error(
                    "Routing service request failed: {}",
                    exception.getMessage()
            );

            throw new IllegalStateException(
                    "Route service failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private RouteResponse parseGeoJson(
            String responseBody
    ) {

        try {

            double distanceMeters =
                    extractDoubleValue(
                            responseBody,
                            "\"distance\""
                    );

            double durationSeconds =
                    extractDoubleValue(
                            responseBody,
                            "\"duration\""
                    );

            if (distanceMeters <= 0 ||
                    durationSeconds <= 0) {

                throw new IllegalStateException(
                        "ORS returned invalid route distance/duration."
                );
            }

            List<List<Double>> routeCoordinates =
                    extractRouteCoordinates(responseBody);

            if (routeCoordinates.isEmpty()) {

                throw new IllegalStateException(
                        "ORS returned no route coordinates."
                );
            }

            log.info(
                    "Route calculated successfully: " +
                    "distance={}m, duration={}s, points={}",
                    distanceMeters,
                    durationSeconds,
                    routeCoordinates.size()
            );

            return new RouteResponse(
                    distanceMeters,
                    durationSeconds,
                    routeCoordinates
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to parse ORS response: {}",
                    exception.getMessage()
            );

            throw new IllegalStateException(
                    "Failed to parse ORS response: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private double extractDoubleValue(
            String json,
            String fieldName
    ) {

        int fieldIndex = json.indexOf(fieldName);

        if (fieldIndex < 0) {
            return -1;
        }

        int valueIndex = json.indexOf(':', fieldIndex);

        if (valueIndex < 0) {
            return -1;
        }

        int cursor = valueIndex + 1;

        while (cursor < json.length() &&
                Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }

        int start = cursor;

        while (cursor < json.length() &&
                (
                        Character.isDigit(json.charAt(cursor)) ||
                                json.charAt(cursor) == '-' ||
                                json.charAt(cursor) == '+' ||
                                json.charAt(cursor) == '.' ||
                                json.charAt(cursor) == 'e' ||
                                json.charAt(cursor) == 'E'
                )) {
            cursor++;
        }

        if (start == cursor) {
            return -1;
        }

        return Double.parseDouble(
                json.substring(start, cursor)
        );
    }

    private List<List<Double>> extractRouteCoordinates(
            String json
    ) {

        int coordinatesIndex = json.indexOf("\"coordinates\"");

        if (coordinatesIndex < 0) {
            return new ArrayList<>();
        }

        int arrayStart = json.indexOf('[', coordinatesIndex);

        if (arrayStart < 0) {
            return new ArrayList<>();
        }

        List<List<Double>> routeCoordinates =
                new ArrayList<>();

        int cursor = arrayStart;

        while (cursor < json.length()) {

            while (cursor < json.length() &&
                    Character.isWhitespace(json.charAt(cursor))) {
                cursor++;
            }

            if (cursor >= json.length()) {
                break;
            }

            if (json.charAt(cursor) == ']') {
                break;
            }

            if (json.charAt(cursor) != '[') {
                cursor++;
                continue;
            }

            int end = findMatchingBracket(json, cursor);

            if (end < 0) {
                break;
            }

            String coordinateBlock =
                    json.substring(cursor, end + 1);

            List<Double> point =
                    parseCoordinatePoint(coordinateBlock);

            if (point != null) {
                routeCoordinates.add(point);
            }

            cursor = end + 1;
        }

        return routeCoordinates;
    }

    private List<Double> parseCoordinatePoint(
            String coordinateBlock
    ) {

        List<Double> numbers = new ArrayList<>();
        int cursor = 0;

        while (cursor < coordinateBlock.length()) {

            while (cursor < coordinateBlock.length() &&
                    Character.isWhitespace(coordinateBlock.charAt(cursor))) {
                cursor++;
            }

            if (cursor >= coordinateBlock.length()) {
                break;
            }

            if (coordinateBlock.charAt(cursor) == '[' ||
                    coordinateBlock.charAt(cursor) == ']') {
                cursor++;
                continue;
            }

            int start = cursor;

            while (cursor < coordinateBlock.length() &&
                    (
                            Character.isDigit(coordinateBlock.charAt(cursor)) ||
                                    coordinateBlock.charAt(cursor) == '-' ||
                                    coordinateBlock.charAt(cursor) == '+' ||
                                    coordinateBlock.charAt(cursor) == '.' ||
                                    coordinateBlock.charAt(cursor) == 'e' ||
                                    coordinateBlock.charAt(cursor) == 'E'
                    )) {
                cursor++;
            }

            if (start == cursor) {
                cursor++;
                continue;
            }

            numbers.add(
                    Double.parseDouble(
                            coordinateBlock.substring(start, cursor)
                    )
            );
        }

        if (numbers.size() < 2) {
            return null;
        }

        return List.of(numbers.get(0), numbers.get(1));
    }

    private int findMatchingBracket(
            String json,
            int startIndex
    ) {

        int depth = 0;

        for (int i = startIndex; i < json.length(); i++) {
            char current = json.charAt(i);

            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }
}