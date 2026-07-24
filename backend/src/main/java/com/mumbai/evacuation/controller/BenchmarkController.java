package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.model.PathResult;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for Pathfinding Algorithm Performance Benchmarking (Dijkstra vs A*).
 */
@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin(origins = "*")
public class BenchmarkController {

    @Autowired
    private GraphService graphService;

    private final DijkstraEngine dijkstraEngine = new DijkstraEngine();
    private final AStarEngine aStarEngine = new AStarEngine();

    /**
     * GET /api/benchmark/algorithms — Benchmark Dijkstra vs A* on key Mumbai routes.
     */
    @GetMapping("/algorithms")
    public ResponseEntity<Map<String, Object>> benchmarkAlgorithms() {
        Graph graph = graphService.getGraph();

        // 3 Key Mumbai Corridors
        long borivaliNode = findNearestNodeId(graph, 19.2307, 72.8567);
        long churchgateNode = findNearestNodeId(graph, 18.9322, 72.8264);

        long dadarNode = findNearestNodeId(graph, 19.0178, 72.8478);
        long andheriNode = findNearestNodeId(graph, 19.1197, 72.8464);

        long cstNode = findNearestNodeId(graph, 18.9401, 72.8351);
        long goregaonNode = findNearestNodeId(graph, 19.1663, 72.8454);

        List<Map<String, Object>> routeBenchmarks = new ArrayList<>();

        routeBenchmarks.add(runPairBenchmark(graph, "Borivali -> Churchgate", borivaliNode, churchgateNode));
        routeBenchmarks.add(runPairBenchmark(graph, "Dadar -> Andheri", dadarNode, andheriNode));
        routeBenchmarks.add(runPairBenchmark(graph, "CST -> Goregaon", cstNode, goregaonNode));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("graphSize", Map.of("nodeCount", graph.getNodeCount(), "edgeCount", graph.getEdgeCount()));
        response.put("benchmarkResults", routeBenchmarks);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> runPairBenchmark(Graph graph, String name, long src, long dst) {
        PathResult dijkstraResult = dijkstraEngine.findShortestPath(graph, src, dst);
        PathResult astarResult = aStarEngine.findShortestPath(graph, src, dst);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("corridorName", name);
        map.put("sourceNodeId", src);
        map.put("targetNodeId", dst);

        Map<String, Object> dMap = new LinkedHashMap<>();
        dMap.put("algorithm", "DIJKSTRA");
        dMap.put("executionTimeMs", dijkstraResult.getExecutionTimeMs());
        dMap.put("executionTimeNs", dijkstraResult.getExecutionTimeNs());
        dMap.put("nodesExplored", dijkstraResult.getNodesExplored());
        dMap.put("totalDistanceKm", dijkstraResult.getTotalDistanceMeters() / 1000.0);
        dMap.put("travelTimeMinutes", dijkstraResult.getTotalTravelTimeMinutes());

        Map<String, Object> aMap = new LinkedHashMap<>();
        aMap.put("algorithm", "ASTAR");
        aMap.put("executionTimeMs", astarResult.getExecutionTimeMs());
        aMap.put("executionTimeNs", astarResult.getExecutionTimeNs());
        aMap.put("nodesExplored", astarResult.getNodesExplored());
        aMap.put("totalDistanceKm", astarResult.getTotalDistanceMeters() / 1000.0);
        aMap.put("travelTimeMinutes", astarResult.getTotalTravelTimeMinutes());

        double searchSpaceReductionPercent = dijkstraResult.getNodesExplored() > 0 ?
            ((double) (dijkstraResult.getNodesExplored() - astarResult.getNodesExplored()) / dijkstraResult.getNodesExplored()) * 100.0 : 0.0;

        map.put("dijkstra", dMap);
        map.put("aStar", aMap);
        map.put("searchSpaceReductionPercent", Math.round(searchSpaceReductionPercent * 10.0) / 10.0);

        return map;
    }

    private long findNearestNodeId(Graph graph, double lat, double lon) {
        Node best = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : graph.getAllNodes()) {
            double dlat = n.getLatitude() - lat;
            double dlon = n.getLongitude() - lon;
            double d = dlat * dlat + dlon * dlon;
            if (d < minDist) {
                minDist = d;
                best = n;
            }
        }
        return best != null ? best.getId() : -1;
    }
}
