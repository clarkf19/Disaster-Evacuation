package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import com.mumbai.evacuation.service.TomTomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LiveRouteController — secure backend proxy for TomTom live traffic routing.
 *
 * POST /api/live-route accepts a JSON body from the frontend.
 * GET  /api/live-route accepts query params (for browser/tool testing).
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
     * Body: { "fromLat": 19.07, "fromLon": 72.87, "toLat": 19.02, "toLon": 72.84 }
     */
    @PostMapping("/live-route")
    public ResponseEntity<LiveRouteResponse> calculateLiveRoutePost(
            @RequestBody LiveRouteRequest request) {
        LiveRouteResponse response = tomTomService.calculateLiveRoute(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/live-route?fromLat=...&fromLon=...&toLat=...&toLon=...
     * Useful for browser testing and health-checks.
     */
    @GetMapping("/live-route")
    public ResponseEntity<LiveRouteResponse> calculateLiveRouteGet(
            @RequestParam(defaultValue = "19.0760") double fromLat,
            @RequestParam(defaultValue = "72.8777") double fromLon,
            @RequestParam(defaultValue = "19.0176") double toLat,
            @RequestParam(defaultValue = "72.8461") double toLon) {

        LiveRouteRequest req = new LiveRouteRequest();
        req.setFromLat(fromLat);
        req.setFromLon(fromLon);
        req.setToLat(toLat);
        req.setToLon(toLon);

        LiveRouteResponse response = tomTomService.calculateLiveRoute(req);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/geocode?lat={lat}&lon={lon}
     * Returns a human-readable place name for the given coordinates.
     */
    @GetMapping("/geocode")
    public ResponseEntity<?> reverseGeocode(
            @RequestParam(defaultValue = "19.0760") double lat,
            @RequestParam(defaultValue = "72.8777") double lon) {
        String name = tomTomService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(java.util.Map.of("name", name, "lat", lat, "lon", lon));
    }

    /**
     * GET /api/search?q={query}&lat={biasLat}&lon={biasLon}
     * Forward geocode — returns place suggestions for autocomplete.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchPlaces(
            @RequestParam String q,
            @RequestParam(defaultValue = "19.18") double lat,
            @RequestParam(defaultValue = "72.93") double lon) {
        var suggestions = tomTomService.searchPlaces(q, lat, lon);
        return ResponseEntity.ok(suggestions);
    }
}
