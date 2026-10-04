package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import com.mumbai.evacuation.dto.PlaceSuggestion;
import com.mumbai.evacuation.service.GeocodingService;
import com.mumbai.evacuation.service.LiveRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Route planner and geocoding endpoints used by the UI. All third-party calls
 * (TomTom, Photon, Nominatim) happen server-side so keys stay secret and the
 * same code path works in development and production.
 */
@RestController
@RequestMapping("/api")
@Validated
public class LiveRouteController {

    private final LiveRouteService liveRouteService;
    private final GeocodingService geocodingService;

    public LiveRouteController(LiveRouteService liveRouteService, GeocodingService geocodingService) {
        this.liveRouteService = liveRouteService;
        this.geocodingService = geocodingService;
    }

    /** POST /api/live-route — hazard-aware route, with live traffic when TomTom is configured. */
    @PostMapping("/live-route")
    public LiveRouteResponse liveRoute(@Valid @RequestBody LiveRouteRequest request) {
        return liveRouteService.route(request);
    }

    /** GET /api/geocode?lat=&lon= — human-readable place name for coordinates. */
    @GetMapping("/geocode")
    public Map<String, Object> reverseGeocode(@RequestParam double lat, @RequestParam double lon) {
        return Map.of("name", geocodingService.reverseGeocode(lat, lon), "lat", lat, "lon", lon);
    }

    /** GET /api/search?q= — place autocomplete limited to the mapped area. */
    @GetMapping("/search")
    public List<PlaceSuggestion> searchPlaces(@RequestParam @Size(max = 100) String q) {
        return geocodingService.search(q);
    }
}
