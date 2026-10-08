package com.shareride.shareride_mobile_backend.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    @Value("${ors.api.key}")
    private String apiKey;

    @Value("${ors.base-url}")
    private String baseUrl;

    public RouteService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public RouteResponse calculateRoute(RouteRequest request) {

        /*
         * Keep application.properties as:
         *
         * ors.base-url=https://api.heigit.org
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

            headers.setAccept(
                    List.of(
                            MediaType.valueOf("application/geo+json"),
                            MediaType.APPLICATION_JSON
                    )
            );

            headers.set(
                    "Authorization",
                    apiKey
            );

            String body =
                    """
                    {
                      "coordinates": [
                        [%s, %s],
                        [%s, %s]
                      ]
                    }
                    """.formatted(
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

            log.info(
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

            JsonNode root =
                    objectMapper.readTree(responseBody);

            JsonNode feature =
                    root.path("features")
                            .path(0);

            if (feature.isMissingNode() ||
                    feature.isNull()) {

                throw new IllegalStateException(
                        "No route feature returned by ORS."
                );
            }

            JsonNode properties =
                    feature.path("properties");

            JsonNode summary =
                    properties.path("summary");

            double distanceMeters =
                    summary.path("distance")
                            .asDouble();

            double durationSeconds =
                    summary.path("duration")
                            .asDouble();

            if (distanceMeters <= 0 ||
                    durationSeconds <= 0) {

                throw new IllegalStateException(
                        "ORS returned invalid route distance/duration."
                );
            }

            JsonNode coordinates =
                    feature
                            .path("geometry")
                            .path("coordinates");

            List<List<Double>> routeCoordinates =
                    new ArrayList<>();

            if (coordinates.isArray()) {

                for (JsonNode coordinate : coordinates) {

                    if (coordinate.isArray()
                            && coordinate.size() >= 2) {

                        List<Double> point =
                                List.of(
                                        coordinate
                                                .get(0)
                                                .asDouble(),

                                        coordinate
                                                .get(1)
                                                .asDouble()
                                );

                        routeCoordinates.add(point);
                    }
                }
            }

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
}