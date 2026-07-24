package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import com.mumbai.evacuation.service.TomTomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LiveRouteController — secure backend proxy for TomTom live traffic routing.
 *
 * The TomTom API key is stored in application.yml and injected into TomTomService.
 * The frontend sends only coordinates; the key never leaves the server.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LiveRouteController {

    private final TomTomService tomTomService;

    public LiveRouteController(TomTomService tomTomService) {
        this.tomTomService = tomTomService;
    }

    /**
     * POST /api/live-route
     * Body: { fromLat, fromLon, toLat, toLon }
     * Returns live traffic-aware route with real-time travel time, delay, and segment data.
     */
    @PostMapping("/live-route")
    public ResponseEntity<?> calculateLiveRoute(@RequestBody LiveRouteRequest request) {
        try {
            LiveRouteResponse response = tomTomService.calculateLiveRoute(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(
                java.util.Map.of(
                    "error", "Live routing unavailable",
                    "message", e.getMessage()
                )
            );
        }
    }

    /**
     * GET /api/geocode?lat={lat}&lon={lon}
     * Returns a human-readable place name for the given coordinates.
     * TomTom reverse geocoding — key used server-side only.
     */
    @GetMapping("/geocode")
    public ResponseEntity<?> reverseGeocode(
            @RequestParam double lat,
            @RequestParam double lon) {
        String name = tomTomService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(java.util.Map.of("name", name, "lat", lat, "lon", lon));
    }
}
