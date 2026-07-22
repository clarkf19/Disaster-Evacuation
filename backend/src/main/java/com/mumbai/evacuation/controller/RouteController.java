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
 * REST controller for route computation.
 * Accepts source/target node IDs and algorithm choice, returns full path with coordinates.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    @Autowired
    private GraphService graphService;

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

    /** GET /api/nodes — returns all nodes (id, lat, lon) for Leaflet rendering */
    @GetMapping("/nodes")
    public ResponseEntity<List<Map<String, Object>>> getNodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node node : graphService.getGraph().getAllNodes()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", node.getId());
            n.put("lat", node.getLatitude());
            n.put("lon", node.getLongitude());
            if (node.getName() != null && !node.getName().startsWith("Node-")) {
                n.put("name", node.getName());
            }
            result.add(n);
        }
        return ResponseEntity.ok(result);
    }

    /** GET /api/nearest?lat=&lon= — find nearest node to coordinates (for map click) */
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
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", best.getId());
        resp.put("lat", best.getLatitude());
        resp.put("lon", best.getLongitude());
        return ResponseEntity.ok(resp);
    }
}
