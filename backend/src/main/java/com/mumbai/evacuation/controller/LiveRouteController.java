package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import com.mumbai.evacuation.service.TomTomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LiveRouteController — secure backend proxy for TomTom live traffic routing.
 *
 * Supports both POST (JSON body) and GET (query params) so requests never fail
 * with 400 Bad Request or 500 Internal Server Error.
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
     */
    @PostMapping("/live-route")
    public ResponseEntity<LiveRouteResponse> calculateLiveRoutePost(
            @RequestBody(required = false) LiveRouteRequest request,
            @RequestParam(required = false) Double fromLat,
            @RequestParam(required = false) Double fromLon,
            @RequestParam(required = false) Double toLat,
            @RequestParam(required = false) Double toLon) {

        LiveRouteRequest req = resolveRequest(request, fromLat, fromLon, toLat, toLon);
        LiveRouteResponse response = tomTomService.calculateLiveRoute(req);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/live-route?fromLat=...&fromLon=...&toLat=...&toLon=...
     */
    @GetMapping("/live-route")
    public ResponseEntity<LiveRouteResponse> calculateLiveRouteGet(
            @RequestParam(required = false, defaultValue = "19.0760") double fromLat,
            @RequestParam(required = false, defaultValue = "72.8777") double fromLon,
            @RequestParam(required = false, defaultValue = "19.0176") double toLat,
            @RequestParam(required = false, defaultValue = "72.8461") double toLon) {

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
            @RequestParam(required = false, defaultValue = "19.0760") double lat,
            @RequestParam(required = false, defaultValue = "72.8777") double lon) {
        String name = tomTomService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(java.util.Map.of("name", name, "lat", lat, "lon", lon));
    }

    /**
     * GET /api/search?q={query}&lat={biasLat}&lon={biasLon}
     * Forward geocode — returns up to 6 place suggestions for the typed query.
     * Used for type-to-search autocomplete in the route planner.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchPlaces(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "19.0760") double lat,
            @RequestParam(required = false, defaultValue = "72.8777") double lon) {
        var suggestions = tomTomService.searchPlaces(q, lat, lon);
        return ResponseEntity.ok(suggestions);
    }

    private LiveRouteRequest resolveRequest(LiveRouteRequest bodyReq, Double fromLat, Double fromLon, Double toLat, Double toLon) {
        if (bodyReq != null && (bodyReq.getFromLat() != 0.0 || bodyReq.getToLat() != 0.0)) {
            return bodyReq;
        }
        LiveRouteRequest req = new LiveRouteRequest();
        req.setFromLat(fromLat != null ? fromLat : 19.0760);
        req.setFromLon(fromLon != null ? fromLon : 72.8777);
        req.setToLat(toLat != null ? toLat : 19.0176);
        req.setToLon(toLon != null ? toLon : 72.8461);
        return req;
    }
}
