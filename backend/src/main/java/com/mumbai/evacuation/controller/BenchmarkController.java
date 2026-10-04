package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.model.PathResult;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Dijkstra vs A* on three long Mumbai corridors, under the live hazard overlay.
 * Both must return the same optimal travel time; A* should explore fewer nodes.
 */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private record Corridor(String name, double fromLat, double fromLon, double toLat, double toLon) {}

    private static final List<Corridor> CORRIDORS = List.of(
            new Corridor("Borivali -> Churchgate", 19.2307, 72.8567, 18.9322, 72.8264),
            new Corridor("Dadar -> Andheri", 19.0178, 72.8478, 19.1197, 72.8464),
            new Corridor("CST -> Goregaon", 18.9401, 72.8351, 19.1663, 72.8454));

    private final GraphService graphService;

    public BenchmarkController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/algorithms")
    public Map<String, Object> benchmarkAlgorithms() {
        EdgeCost cost = graphService.getHazardOverlay().asEdgeCost();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Corridor c : CORRIDORS) {
            long src = graphService.snap(c.fromLat(), c.fromLon()).node().getId();
            long dst = graphService.snap(c.toLat(), c.toLon()).node().getId();
            PathResult dijkstra = graphService.shortestPath(src, dst, "DIJKSTRA", cost);
            PathResult aStar = graphService.shortestPath(src, dst, "ASTAR", cost);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("corridorName", c.name());
            m.put("dijkstra", stats(dijkstra));
            m.put("aStar", stats(aStar));
            m.put("costsMatch", dijkstra.isPathFound() == aStar.isPathFound()
                    && Math.abs(dijkstra.getTotalTravelTimeSeconds() - aStar.getTotalTravelTimeSeconds()) < 1e-6);
            m.put("searchSpaceReductionPercent", dijkstra.getNodesExplored() > 0
                    ? Math.round(1000.0 * (dijkstra.getNodesExplored() - aStar.getNodesExplored()) / dijkstra.getNodesExplored()) / 10.0
                    : 0.0);
            results.add(m);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("graphSize", Map.of("nodeCount", graphService.getGraph().getNodeCount(),
                "edgeCount", graphService.getGraph().getEdgeCount()));
        response.put("activeHazards", graphService.getActiveDisasters().size());
        response.put("benchmarkResults", results);
        return response;
    }

    private static Map<String, Object> stats(PathResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pathFound", r.isPathFound());
        m.put("executionTimeMs", Math.round(r.getExecutionTimeMs() * 1000.0) / 1000.0);
        m.put("nodesExplored", r.getNodesExplored());
        m.put("totalDistanceKm", Math.round(r.getTotalDistanceMeters() / 10.0) / 100.0);
        m.put("travelTimeMinutes", Math.round(r.getTotalTravelTimeMinutes() * 100.0) / 100.0);
        return m;
    }
}
