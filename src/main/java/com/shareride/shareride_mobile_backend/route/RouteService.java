package com.shareride.shareride_mobile_backend.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    @Value("${ors.api.key:}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RouteService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public RouteResponse calculateRoute(RouteRequest request) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ORS_API_KEY is not configured."
            );
        }

        String cleanApiKey = apiKey.trim();

        String url =
                baseUrl + "/v2/directions/driving-car/geojson";

        System.out.println("ORS URL: " + url);
        System.out.println(
                "ORS API key loaded: yes (" +
                cleanApiKey.length() +
                " characters)"
        );

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // ORS expects the API key directly in Authorization.
        headers.set("Authorization", cleanApiKey);

        String body = String.format(
                "{\n" +
                        "  \"coordinates\": [\n" +
                        "    [%f, %f],\n" +
                        "    [%f, %f]\n" +
                        "  ]\n" +
                        "}",
                request.pickupLongitude(),
                request.pickupLatitude(),
                request.destinationLongitude(),
                request.destinationLatitude()
        );

        HttpEntity<String> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class
                );

        try {

            JsonNode root =
                    objectMapper.readTree(response.getBody());

            if (root.has("error")) {

                String message =
                        root.path("error")
                                .path("message")
                                .toString();

                throw new RuntimeException(
                        "OpenRouteService error: " + message
                );
            }

            JsonNode features =
                    root.path("features");

            if (!features.isArray()
                    || features.isEmpty()) {

                throw new RuntimeException(
                        "No route found between the selected locations."
                );
            }

            JsonNode feature =
                    features.get(0);

            JsonNode summary =
                    feature
                            .path("properties")
                            .path("summary");

            double distance =
                    summary
                            .path("distance")
                            .asDouble();

            double duration =
                    summary
                            .path("duration")
                            .asDouble();

            JsonNode coordinates =
                    feature
                            .path("geometry")
                            .path("coordinates");

            if (!coordinates.isArray()
                    || coordinates.isEmpty()) {

                throw new RuntimeException(
                        "Route geometry was not returned."
                );
            }

            List<List<Double>> routeCoordinates =
                    new ArrayList<>();

            for (JsonNode point : coordinates) {

                if (point.size() < 2) {
                    continue;
                }

                routeCoordinates.add(
                        List.of(
                                point.get(0).asDouble(),
                                point.get(1).asDouble()
                        )
                );
            }

            if (routeCoordinates.isEmpty()) {
                throw new RuntimeException(
                        "No valid route coordinates returned."
                );
            }

            return new RouteResponse(
                    distance,
                    duration,
                    routeCoordinates
            );

        } catch (RuntimeException e) {
            throw e;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to parse OpenRouteService response.",
                    e
            );
        }
    }
}