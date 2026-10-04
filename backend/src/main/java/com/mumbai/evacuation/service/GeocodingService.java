package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.dto.PlaceSuggestion;
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
 * Place search (autocomplete) and reverse geocoding, proxied through the
 * backend so it works identically in development and production and so third
 * party usage policies are respected (identifying User-Agent, caching).
 *
 * Search: Photon (OSM, built for autocomplete), restricted to the road-graph
 * coverage area; TomTom search as fallback when a key is configured.
 * Reverse: Nominatim (OSM) with TomTom as fallback, then raw coordinates.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final int CACHE_SIZE = 2_000;

    private final ObjectMapper objectMapper;
    private final TomTomService tomTomService;
    private final GraphService graphService;
    private final String userAgent;
    private final String photonUrl;
    private final String nominatimUrl;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, List<PlaceSuggestion>> searchCache = lruCache();
    private final Map<String, String> reverseCache = lruCache();

    public GeocodingService(ObjectMapper objectMapper, TomTomService tomTomService, GraphService graphService,
                            @Value("${geocoding.user-agent:MumbaiDisasterEvacuationSystem/1.1 (student project)}") String userAgent,
                            @Value("${geocoding.photon-url:https://photon.komoot.io/api/}") String photonUrl,
                            @Value("${geocoding.nominatim-url:https://nominatim.openstreetmap.org/reverse}") String nominatimUrl) {
        this.objectMapper = objectMapper;
        this.tomTomService = tomTomService;
        this.graphService = graphService;
        this.userAgent = userAgent;
        this.photonUrl = photonUrl;
        this.nominatimUrl = nominatimUrl;
    }

    public List<PlaceSuggestion> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        String key = q.toLowerCase(Locale.ROOT);
        synchronized (searchCache) {
            List<PlaceSuggestion> cached = searchCache.get(key);
            if (cached != null) return cached;
        }

        double[] b = graphService.getGraph().getBounds(); // minLat, minLon, maxLat, maxLon
        double biasLat = (b[0] + b[2]) / 2, biasLon = (b[1] + b[3]) / 2;
        List<PlaceSuggestion> results = new ArrayList<>(searchPhoton(q, b, biasLat, biasLon));
        if (results.size() < 3) results.addAll(searchTomTom(q, b, biasLat, biasLon, results));

        List<PlaceSuggestion> immutable = List.copyOf(results);
        synchronized (searchCache) { searchCache.put(key, immutable); }
        return immutable;
    }

    private List<PlaceSuggestion> searchPhoton(String q, double[] b, double biasLat, double biasLon) {
        List<PlaceSuggestion> out = new ArrayList<>();
        try {
            String url = String.format(Locale.US, "%s?q=%s&lat=%.4f&lon=%.4f&limit=12&lang=en&bbox=%.4f,%.4f,%.4f,%.4f",
                    photonUrl, enc(q), biasLat, biasLon, b[1], b[0], b[3], b[2]);
            JsonNode root = getJson(url);
            if (root == null) return out;
            for (JsonNode f : root.path("features")) {
                JsonNode p = f.path("properties");
                JsonNode c = f.path("geometry").path("coordinates");
                double lon = c.path(0).asDouble(), lat = c.path(1).asDouble();
                String name = firstNonBlank(p.path("name").asText(""), p.path("street").asText(""), p.path("city").asText(""));
                if (name.isBlank() || isDuplicate(out, lat, lon)) continue;
                List<String> sub = new ArrayList<>();
                for (String field : new String[]{"locality", "district", "city"}) {
                    String v = p.path(field).asText("");
                    if (!v.isBlank() && !sub.contains(v) && !v.equals(name)) sub.add(v);
                }
                String osmKey = p.path("osm_key").asText(""), osmValue = p.path("osm_value").asText("");
                out.add(new PlaceSuggestion(name, sub.isEmpty() ? "Mumbai" : String.join(", ", sub), lat, lon,
                        osmValue.isBlank() ? "place" : osmValue, PlaceIcons.resolve(name, osmKey, osmValue)));
            }
        } catch (Exception e) {
            log.warn("Photon search failed: {}", e.getClass().getSimpleName());
        }
        return out;
    }

    private List<PlaceSuggestion> searchTomTom(String q, double[] b, double biasLat, double biasLon, List<PlaceSuggestion> existing) {
        List<PlaceSuggestion> out = new ArrayList<>();
        tomTomService.search(q, biasLat, biasLon).ifPresent(items -> {
            for (JsonNode item : items) {
                double lat = item.path("position").path("lat").asDouble();
                double lon = item.path("position").path("lon").asDouble();
                if (lat < b[0] || lat > b[2] || lon < b[1] || lon > b[3]) continue; // outside coverage
                JsonNode addr = item.path("address");
                String name = firstNonBlank(item.path("poi").path("name").asText(""), addr.path("streetName").asText(""),
                        addr.path("municipalitySubdivision").asText(""), addr.path("freeformAddress").asText(""));
                if (name.isBlank() || isDuplicate(existing, lat, lon) || isDuplicate(out, lat, lon)) continue;
                out.add(new PlaceSuggestion(name, addr.path("freeformAddress").asText("Mumbai"), lat, lon,
                        item.path("type").asText("POI"), PlaceIcons.resolve(name, "", "")));
            }
        });
        return out;
    }

    public String reverseGeocode(double lat, double lon) {
        String key = String.format(Locale.US, "%.4f,%.4f", lat, lon);
        synchronized (reverseCache) {
            String cached = reverseCache.get(key);
            if (cached != null) return cached;
        }
        String name = reverseNominatim(lat, lon)
                .or(() -> tomTomService.reverseGeocode(lat, lon))
                .orElse(String.format(Locale.US, "%.4f, %.4f", lat, lon));
        synchronized (reverseCache) { reverseCache.put(key, name); }
        return name;
    }

    private Optional<String> reverseNominatim(double lat, double lon) {
        try {
            String url = String.format(Locale.US, "%s?lat=%.6f&lon=%.6f&format=json&addressdetails=1&zoom=17", nominatimUrl, lat, lon);
            JsonNode root = getJson(url);
            if (root == null) return Optional.empty();
            JsonNode a = root.path("address");
            String specific = firstNonBlank(a.path("road").asText(""), a.path("pedestrian").asText(""),
                    a.path("suburb").asText(""), a.path("neighbourhood").asText(""));
            String area = firstNonBlank(a.path("suburb").asText(""), a.path("neighbourhood").asText(""),
                    a.path("city_district").asText(""));
            String city = firstNonBlank(a.path("city").asText(""), a.path("town").asText(""), "Mumbai");
            if (!specific.isBlank() && !area.isBlank() && !specific.equals(area)) return Optional.of(specific + ", " + area);
            if (!specific.isBlank()) return Optional.of(specific + ", " + city);
            if (!area.isBlank()) return Optional.of(area + ", " + city);
        } catch (Exception e) {
            log.warn("Nominatim reverse geocode failed: {}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? objectMapper.readTree(res.body()) : null;
    }

    private static boolean isDuplicate(List<PlaceSuggestion> list, double lat, double lon) {
        return list.stream().anyMatch(s -> Math.abs(s.getLat() - lat) < 0.0015 && Math.abs(s.getLon() - lon) < 0.0015);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static <K, V> Map<K, V> lruCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > CACHE_SIZE; }
        };
    }
}
