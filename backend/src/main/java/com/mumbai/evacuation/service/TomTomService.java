package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mumbai.evacuation.disaster.DisasterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Thin server-side client for the TomTom Routing and Search APIs.
 * The API key never leaves the backend and is never written to logs.
 */
@Service
public class TomTomService {

    private static final Logger log = LoggerFactory.getLogger(TomTomService.class);
    /** TomTom accepts at most 10 avoid-area rectangles per request. */
    static final int MAX_AVOID_AREAS = 10;

    private final String apiKey;
    private final String routingBaseUrl;
    private final String reverseGeocodeBaseUrl;
    private final String searchBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TomTomService(ObjectMapper objectMapper,
                         @Value("${tomtom.api.key:}") String apiKey,
                         @Value("${tomtom.api.routing-base-url:https://api.tomtom.com/routing/1/calculateRoute}") String routingBaseUrl,
                         @Value("${tomtom.api.geocode-base-url:https://api.tomtom.com/search/2/reverseGeocode}") String reverseGeocodeBaseUrl,
                         @Value("${tomtom.api.search-base-url:https://api.tomtom.com/search/2/search}") String searchBaseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.routingBaseUrl = routingBaseUrl;
        this.reverseGeocodeBaseUrl = reverseGeocodeBaseUrl;
        this.searchBaseUrl = searchBaseUrl;
        if (!isConfigured()) {
            log.warn("TOMTOM_API_KEY not set — live traffic disabled, routes use the hazard-aware road graph only.");
        }
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.startsWith("your_");
    }

    /** A TomTom route: geometry plus summary and traffic sections. */
    public record TomTomRoute(List<double[]> points, long travelSeconds, long noTrafficSeconds, long delaySeconds,
                              double lengthMeters, List<TrafficSection> trafficSections) {}

    public record TrafficSection(int startPointIndex, int endPointIndex, int magnitudeOfDelay) {}

    /**
     * Fastest car route with live traffic, avoiding a bounding box around each
     * hazard zone. Returns empty if TomTom is not configured, fails, or finds no route.
     */
    public Optional<TomTomRoute> route(double fromLat, double fromLon, double toLat, double toLon,
                                       List<DisasterEvent> avoid) {
        if (!isConfigured()) return Optional.empty();
        try {
            String coords = String.format(Locale.US, "%.6f,%.6f:%.6f,%.6f", fromLat, fromLon, toLat, toLon);
            String url = String.format(Locale.US, "%s/%s/json?key=%s&traffic=true&computeTravelTimeFor=all"
                    + "&routeType=fastest&travelMode=car&sectionType=traffic", routingBaseUrl, coords, enc(apiKey));

            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode rectangles = body.putObject("avoidAreas").putArray("rectangles");
            for (DisasterEvent d : avoid.subList(0, Math.min(avoid.size(), MAX_AVOID_AREAS))) {
                double dLat = d.getAffectedRadiusMeters() / 111_320.0;
                double dLon = d.getAffectedRadiusMeters() / (111_320.0 * Math.cos(Math.toRadians(d.getCenterLatitude())));
                ObjectNode rect = rectangles.addObject();
                rect.putObject("southWestCorner").put("latitude", d.getCenterLatitude() - dLat).put("longitude", d.getCenterLongitude() - dLon);
                rect.putObject("northEastCorner").put("latitude", d.getCenterLatitude() + dLat).put("longitude", d.getCenterLongitude() + dLon);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("TomTom routing returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode routes = objectMapper.readTree(response.body()).path("routes");
            if (!routes.isArray() || routes.isEmpty()) return Optional.empty();
            return Optional.of(parseRoute(routes.get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("TomTom routing failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private TomTomRoute parseRoute(JsonNode route) {
        JsonNode summary = route.path("summary");
        long live = summary.path("travelTimeInSeconds").asLong();
        long noTraffic = summary.path("noTrafficTravelTimeInSeconds").asLong(live);
        long delay = summary.path("trafficDelayInSeconds").asLong(Math.max(0, live - noTraffic));
        double length = summary.path("lengthInMeters").asDouble();

        List<double[]> points = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode pt : leg.path("points")) {
                points.add(new double[]{pt.path("latitude").asDouble(), pt.path("longitude").asDouble()});
            }
        }
        List<TrafficSection> sections = new ArrayList<>();
        for (JsonNode sec : route.path("sections")) {
            if ("TRAFFIC".equals(sec.path("sectionType").asText())) {
                sections.add(new TrafficSection(sec.path("startPointIndex").asInt(0),
                        sec.path("endPointIndex").asInt(0), sec.path("magnitudeOfDelay").asInt(0)));
            }
        }
        return new TomTomRoute(points, live, noTraffic, delay, length, sections);
    }

    /** Reverse geocode to a short place name, or empty. */
    public Optional<String> reverseGeocode(double lat, double lon) {
        if (!isConfigured()) return Optional.empty();
        try {
            String url = String.format(Locale.US, "%s/%.6f,%.6f.json?key=%s&radius=150", reverseGeocodeBaseUrl, lat, lon, enc(apiKey));
            JsonNode root = getJson(url);
            if (root == null) return Optional.empty();
            JsonNode addresses = root.path("addresses");
            if (addresses.isEmpty()) return Optional.empty();
            JsonNode addr = addresses.get(0).path("address");
            for (String field : new String[]{"localName", "municipalitySubdivision", "municipality", "freeformAddress"}) {
                String val = addr.path(field).asText("");
                if (!val.isBlank()) return Optional.of(val);
            }
        } catch (Exception e) {
            log.warn("TomTom reverse geocode failed: {}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /** Raw TomTom fuzzy-search results (JSON "results" array), or empty. */
    public Optional<JsonNode> search(String query, double biasLat, double biasLon) {
        if (!isConfigured()) return Optional.empty();
        try {
            String url = String.format(Locale.US,
                    "%s/%s.json?key=%s&countrySet=IN&lat=%.6f&lon=%.6f&radius=60000&limit=10&idxSet=POI,PAD,Str,Geo",
                    searchBaseUrl, enc(query), enc(apiKey), biasLat, biasLon);
            JsonNode root = getJson(url);
            return root == null ? Optional.empty() : Optional.of(root.path("results"));
        } catch (Exception e) {
            log.warn("TomTom search failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json").timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? objectMapper.readTree(response.body()) : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
