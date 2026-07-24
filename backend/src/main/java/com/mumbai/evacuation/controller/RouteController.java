package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.RouteRequest;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for route computation and location lookup.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    @Autowired
    private GraphService graphService;

    private static final List<Landmark> LANDMARKS = Arrays.asList(
        new Landmark("Borivali", 19.2307, 72.8567),
        new Landmark("Kandivali", 19.2070, 72.8540),
        new Landmark("Malad", 19.1860, 72.8485),
        new Landmark("Goregaon", 19.1663, 72.8454),
        new Landmark("Andheri West", 19.1197, 72.8464),
        new Landmark("Andheri East", 19.1136, 72.8697),
        new Landmark("Santa Cruz", 19.0843, 72.8360),
        new Landmark("Bandra West", 19.0596, 72.8295),
        new Landmark("Bandra Kurla Complex (BKC)", 19.0657, 72.8686),
        new Landmark("Dadar West", 19.0178, 72.8478),
        new Landmark("Worli", 19.0134, 72.8179),
        new Landmark("Prabhadevi", 19.0166, 72.8296),
        new Landmark("Byculla", 18.9750, 72.8333),
        new Landmark("Marine Drive", 18.9440, 72.8230),
        new Landmark("Churchgate", 18.9322, 72.8264),
        new Landmark("CST (CSMT)", 18.9401, 72.8351),
        new Landmark("Colaba", 18.9067, 72.8147),
        new Landmark("Sion", 19.0390, 72.8619),
        new Landmark("Kurla", 19.0650, 72.8790),
        new Landmark("Chembur", 19.0622, 72.8974),
        new Landmark("Ghatkopar", 19.0860, 72.9080),
        new Landmark("Vikhroli", 19.1110, 72.9280),
        new Landmark("Thane", 19.1860, 72.9630),
        new Landmark("Powai", 19.1176, 72.9060),
        new Landmark("Mulund", 19.1726, 72.9565)
    );

    /** POST /api/route  — compute shortest path */
    @PostMapping("/route")
    public ResponseEntity<RouteResponse> computeRoute(@RequestBody RouteRequest request) {
        RouteResponse response = graphService.computeRoute(request);
        return ResponseEntity.ok(response);
    }

    /** GET /api/graph/stats — graph size metadata */
    @GetMapping("/graph/stats")
    public ResponseEntity<Map<String, Object>> graphStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodeCount", graphService.getNodeCount());
        stats.put("edgeCount", graphService.getEdgeCount());
        return ResponseEntity.ok(stats);
    }

    /** GET /api/nodes — returns all nodes for rendering */
    @GetMapping("/nodes")
    public ResponseEntity<List<Map<String, Object>>> getNodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node node : graphService.getGraph().getAllNodes()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", node.getId());
            n.put("lat", node.getLatitude());
            n.put("lon", node.getLongitude());
            String placeName = resolvePlaceName(node.getLatitude(), node.getLongitude(), node.getName());
            n.put("name", placeName);
            result.add(n);
        }
        return ResponseEntity.ok(result);
    }

    /** GET /api/nearest?lat=&lon= — find nearest node and friendly place name */
    @GetMapping("/nearest")
    public ResponseEntity<Map<String, Object>> nearestNode(
            @RequestParam double lat, @RequestParam double lon) {
        Node best = null;
        double minDist = Double.MAX_VALUE;
        for (Node node : graphService.getGraph().getAllNodes()) {
            double dlat = node.getLatitude() - lat;
            double dlon = node.getLongitude() - lon;
            double d = dlat * dlat + dlon * dlon;
            if (d < minDist) { minDist = d; best = node; }
        }
        if (best == null) return ResponseEntity.notFound().build();

        String placeName = resolvePlaceName(best.getLatitude(), best.getLongitude(), best.getName());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", best.getId());
        resp.put("lat", best.getLatitude());
        resp.put("lon", best.getLongitude());
        resp.put("name", placeName);
        return ResponseEntity.ok(resp);
    }

    private String resolvePlaceName(double lat, double lon, String rawName) {
        if (rawName != null && !rawName.startsWith("Node-") && !rawName.isBlank()) {
            return rawName;
        }

        Landmark closest = null;
        double minDist = Double.MAX_VALUE;
        for (Landmark lm : LANDMARKS) {
            double dlat = lm.lat - lat;
            double dlon = lm.lon - lon;
            double d = dlat * dlat + dlon * dlon;
            if (d < minDist) {
                minDist = d;
                closest = lm;
            }
        }

        if (closest != null) {
            return "Near " + closest.name;
        }
        return "Mumbai Location";
    }

    private static class Landmark {
        final String name;
        final double lat;
        final double lon;

        Landmark(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
