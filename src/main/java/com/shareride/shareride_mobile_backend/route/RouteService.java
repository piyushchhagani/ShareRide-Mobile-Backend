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

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouteService {

    private static final Logger log =
            LoggerFactory.getLogger(RouteService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ors.api.key}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    public RouteService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public RouteResponse calculateRoute(
            RouteRequest request
    ) {

        String url =
                baseUrl
                        + "/openrouteservice/v2/directions/"
                        + "driving-car/geojson";

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        headers.set(
                "Authorization",
                apiKey
        );

        headers.setAccept(
                List.of(
                        MediaType.APPLICATION_JSON
                )
        );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "coordinates",
                new double[][]{
                        {
                                request.pickupLongitude(),
                                request.pickupLatitude()
                        },
                        {
                                request.destinationLongitude(),
                                request.destinationLatitude()
                        }
                }
        );

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(
                        body,
                        headers
                );

        try {

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            return parseRouteResponse(
                    response.getBody()
            );

        } catch (HttpClientErrorException exception) {

            log.error(
                    "Routing service rejected request: status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );

            throw new RuntimeException(
                    "Route service failed: "
                            + exception.getStatusCode()
            );

        } catch (Exception exception) {

            log.error(
                    "Routing service request failed",
                    exception
            );

            throw new RuntimeException(
                    "Unable to calculate route",
                    exception
            );
        }
    }

    private RouteResponse parseRouteResponse(
            String responseBody
    ) {

        try {

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            if (root.has("error")) {

                String message =
                        root.path("error")
                                .path("message")
                                .asText(
                                        "Unknown routing service error"
                                );

                throw new RuntimeException(
                        "Routing service error: "
                                + message
                );
            }

            JsonNode features =
                    root.path("features");

            if (!features.isArray()
                    || features.isEmpty()) {

                throw new RuntimeException(
                        "Routing service returned no route."
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
                        "Routing service returned no coordinates."
                );
            }

            List<List<Double>> routeCoordinates =
                    objectMapper.convertValue(
                            coordinates,
                            List.class
                    );

            return new RouteResponse(
                    distance,
                    duration,
                    routeCoordinates
            );

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Unable to parse route response",
                    exception
            );
        }
    }
}