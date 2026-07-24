package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * TomTomService — server-side proxy for the TomTom Routing & Geocoding APIs.
 *
 * Security Design: The TomTom API key is injected from application.yml via @Value.
 * It is NEVER sent to the browser or exposed in any frontend response.
 * All TomTom calls originate from this service running on the backend.
 *
 * The frontend calls POST /api/live-route with plain coordinates,
 * and this service handles the TomTom interaction transparently.
 */
@Service
public class TomTomService {

    @Value("${tomtom.api.key}")
    private String apiKey;

    @Value("${tomtom.api.routing-base-url}")
    private String routingBaseUrl;

    @Value("${tomtom.api.geocode-base-url}")
    private String geocodeBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calculate a live traffic-aware route between two coordinate pairs.
     * Calls TomTom Routing API with live traffic enabled.
     *
     * @param request source and destination lat/lon
     * @return LiveRouteResponse with real-time travel time, delay, segments
     */
    public LiveRouteResponse calculateLiveRoute(LiveRouteRequest request) throws Exception {
        String coords = String.format("%f,%f:%f,%f",
                request.getFromLat(), request.getFromLon(),
                request.getToLat(), request.getToLon());

        String url = String.format("%s/%s/json?key=%s&traffic=true&computeTravelTimeFor=all" +
                        "&routeType=fastest&travelMode=car&sectionType=traffic",
                routingBaseUrl, coords, apiKey);

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> httpRes = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (httpRes.statusCode() != 200) {
            throw new RuntimeException("TomTom routing API error: HTTP " + httpRes.statusCode());
        }

        JsonNode root = objectMapper.readTree(httpRes.body());
        JsonNode routes = root.get("routes");
        if (routes == null || routes.isEmpty()) {
            throw new RuntimeException("No route found between specified coordinates.");
        }

        return parseRouteResponse(routes.get(0));
    }

    /**
     * Reverse geocode a lat/lon to a place name using TomTom Search API.
     * Called server-side so the key never leaves the backend.
     */
    public String reverseGeocode(double lat, double lon) {
        try {
            String url = String.format("%s/%f,%f.json?key=%s&radius=150",
                    geocodeBaseUrl, lat, lon, apiKey);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return coordinateLabel(lat, lon);

            JsonNode root = objectMapper.readTree(res.body());
            JsonNode addresses = root.path("addresses");
            if (!addresses.isEmpty()) {
                JsonNode addr = addresses.get(0).path("address");
                // Prefer neighbourhood name, then municipality subdivision, then freeform
                for (String field : new String[]{"localName", "municipalitySubdivision", "municipality", "freeformAddress"}) {
                    String val = addr.path(field).asText("");
                    if (!val.isBlank()) return val;
                }
            }
        } catch (Exception e) {
            // Fall through to coordinate label
        }
        return coordinateLabel(lat, lon);
    }

    private String coordinateLabel(double lat, double lon) {
        return String.format("%.4f, %.4f", lat, lon);
    }

    private LiveRouteResponse parseRouteResponse(JsonNode route) {
        JsonNode summary = route.path("summary");

        long liveSeconds     = summary.path("travelTimeInSeconds").asLong();
        long freeFlowSeconds = summary.path("noTrafficTravelTimeInSeconds").asLong();
        long delaySeconds    = summary.path("trafficDelayInSeconds").asLong(Math.max(0, liveSeconds - freeFlowSeconds));
        double distanceM     = summary.path("lengthInMeters").asDouble();

        // Build route geometry from legs → points
        List<double[]> points = new ArrayList<>();
        JsonNode legs = route.path("legs");
        for (JsonNode leg : legs) {
            for (JsonNode pt : leg.path("points")) {
                points.add(new double[]{pt.path("latitude").asDouble(), pt.path("longitude").asDouble()});
            }
        }

        // Build traffic segments from sections (magnitudeOfDelay: 0=none, 1=minor, 2=moderate, 3=major)
        List<LiveRouteResponse.SegmentInfo> segments = buildSegments(route, points);

        int liveMins     = (int) Math.round(liveSeconds / 60.0);
        int freeFlowMins = (int) Math.round(freeFlowSeconds / 60.0);
        int delayMins    = (int) Math.round(delaySeconds / 60.0);

        String status   = resolveStatus(delaySeconds);
        String advisory = buildAdvisory(status, delayMins, distanceM / 1000.0);

        LiveRouteResponse resp = new LiveRouteResponse();
        resp.setPathFound(true);
        resp.setDistanceKm(distanceM / 1000.0);
        resp.setLiveTravelTimeMinutes(liveMins);
        resp.setFreeFlowTravelTimeMinutes(freeFlowMins);
        resp.setDelayMinutes(delayMins);
        resp.setLiveStatus(status);
        resp.setAdvisoryMessage(advisory);
        resp.setRouteCoordinates(points);
        resp.setSegments(segments);
        return resp;
    }

    private List<LiveRouteResponse.SegmentInfo> buildSegments(JsonNode route, List<double[]> points) {
        List<LiveRouteResponse.SegmentInfo> segments = new ArrayList<>();
        List<JsonNode> trafficSections = new ArrayList<>();

        for (JsonNode sec : route.path("sections")) {
            if ("TRAFFIC".equals(sec.path("sectionType").asText())) {
                trafficSections.add(sec);
            }
        }

        if (trafficSections.isEmpty() || points.size() < 2) {
            if (!points.isEmpty()) {
                segments.add(segment(points.get(0), points.get(points.size() - 1), 1.0));
            }
            return segments;
        }

        int cursor = 0;
        for (JsonNode sec : trafficSections) {
            int start = sec.path("startPointIndex").asInt(0);
            int end   = sec.path("endPointIndex").asInt(points.size() - 1);
            int mag   = sec.path("magnitudeOfDelay").asInt(0);

            // Clear gap before this traffic section
            if (cursor < start && start < points.size()) {
                segments.add(segment(points.get(cursor), points.get(start), 1.0));
            }

            // Traffic section with congestion factor
            double factor = mag >= 3 ? 2.5 : mag >= 2 ? 1.7 : mag >= 1 ? 1.3 : 1.0;
            int safeEnd = Math.min(end, points.size() - 1);
            if (start <= safeEnd) {
                segments.add(segment(points.get(start), points.get(safeEnd), factor));
            }
            cursor = safeEnd;
        }

        // Remaining clear tail
        if (cursor < points.size() - 1) {
            segments.add(segment(points.get(cursor), points.get(points.size() - 1), 1.0));
        }

        return segments;
    }

    private LiveRouteResponse.SegmentInfo segment(double[] from, double[] to, double congestion) {
        LiveRouteResponse.SegmentInfo seg = new LiveRouteResponse.SegmentInfo();
        seg.setStartLat(from[0]);
        seg.setStartLon(from[1]);
        seg.setEndLat(to[0]);
        seg.setEndLon(to[1]);
        seg.setCongestionFactor(congestion);
        return seg;
    }

    private String resolveStatus(long delaySeconds) {
        if (delaySeconds >= 600) return "HEAVY_CONGESTION";
        if (delaySeconds >= 180) return "MODERATE_TRAFFIC";
        if (delaySeconds >= 60)  return "SLOW_TRAFFIC";
        return "CLEAR";
    }

    private String buildAdvisory(String status, int delayMins, double distKm) {
        return switch (status) {
            case "HEAVY_CONGESTION" ->
                String.format("Heavy live traffic detected. Current delay: +%d mins. Route optimized for fastest available corridor.", delayMins);
            case "MODERATE_TRAFFIC" ->
                String.format("Moderate traffic on this route. Current delay: +%d mins. This is the fastest available route right now.", delayMins);
            case "SLOW_TRAFFIC" ->
                String.format("Slight slowdown detected (+%d min). Route is clear for most of the %.1f km journey.", delayMins, distKm);
            default ->
                String.format("Live route clear. Free-flow conditions across all %.1f km. No significant delays.", distKm);
        };
    }
}
