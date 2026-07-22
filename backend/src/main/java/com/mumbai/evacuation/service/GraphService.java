package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.disaster.DisasterEngine;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.RouteRequest;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.loader.CsvGraphLoader;
import com.mumbai.evacuation.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Core Graph Service — owns the in-memory graph, disaster engine, and shelter service.
 *
 * Design Decision: Single service instance holds the entire graph in memory.
 * ConcurrentHashMap-based data structures in Graph ensure thread-safe reads.
 * The disaster engine uses synchronized mutation, and route queries are read-only
 * traversals, making concurrent request handling safe without locks on the happy path.
 */
@Service
public class GraphService {

    private Graph graph;
    private final DisasterEngine disasterEngine = new DisasterEngine();
    private final DijkstraEngine dijkstraEngine = new DijkstraEngine();
    private final AStarEngine aStarEngine = new AStarEngine();
    private final ShelterService shelterService = new ShelterService();

    private static final String[] CANDIDATE_PATHS = {
        "data/mumbai_nodes.csv",
        "../data/mumbai_nodes.csv",
        "C:/Users/Anushka/.gemini/antigravity/scratch/mumbai-evacuation-system/data/mumbai_nodes.csv"
    };

    @PostConstruct
    public void initialize() {
        for (String basePath : CANDIDATE_PATHS) {
            File nodesFile = new File(basePath);
            if (nodesFile.exists()) {
                String edgesPath = basePath.replace("mumbai_nodes.csv", "mumbai_edges.csv");
                try {
                    graph = CsvGraphLoader.loadGraphFromCsv(nodesFile.getAbsolutePath(), edgesPath);
                    System.out.println("[GraphService] Loaded " + graph.getNodeCount() + " nodes and " + graph.getEdgeCount() + " edges.");
                    shelterService.initializeMumbaiShelters(graph);
                    System.out.println("[GraphService] Initialized " + shelterService.getAllShelters().size() + " shelters.");
                    return;
                } catch (IOException e) {
                    System.err.println("[GraphService] Failed to load from " + basePath + ": " + e.getMessage());
                }
            }
        }
        // Fallback: empty graph
        graph = new Graph();
        System.err.println("[GraphService] WARNING: Mumbai CSV data not found. Graph is empty.");
    }

    // ---- Route Computation ----

    public RouteResponse computeRoute(RouteRequest request) {
        PathResult result;
        String algo = request.getAlgorithm() == null ? "ASTAR" : request.getAlgorithm().toUpperCase();

        if ("DIJKSTRA".equals(algo)) {
            result = dijkstraEngine.findShortestPath(graph, request.getSourceNodeId(), request.getTargetNodeId());
        } else {
            result = aStarEngine.findShortestPath(graph, request.getSourceNodeId(), request.getTargetNodeId());
            algo = "ASTAR";
        }

        return buildRouteResponse(result, request.getSourceNodeId(), request.getTargetNodeId(), algo);
    }

    private RouteResponse buildRouteResponse(PathResult result, long srcId, long dstId, String algo) {
        RouteResponse response = new RouteResponse();
        response.setPathFound(result.isPathFound());
        response.setSourceNodeId(srcId);
        response.setTargetNodeId(dstId);
        response.setTotalDistanceKm(result.getTotalDistanceMeters() / 1000.0);
        response.setTotalTravelTimeMinutes(result.getTotalTravelTimeMinutes());
        response.setNodesExplored(result.getNodesExplored());
        response.setExecutionTimeMs(result.getExecutionTimeMs());
        response.setAlgorithmUsed(algo);

        List<double[]> coords = new ArrayList<>();
        for (Node node : result.getPathNodes()) {
            coords.add(new double[]{node.getLatitude(), node.getLongitude()});
        }
        response.setRawCoordinates(coords);
        return response;
    }

    // ---- Disaster Management ----

    public void addDisaster(DisasterRequest req) {
        DisasterType type = DisasterType.valueOf(req.getType().toUpperCase());
        DisasterEvent event = new DisasterEvent(
            req.getId(), type, req.getLatitude(), req.getLongitude(),
            req.getRadiusMeters(), req.isBlockRoads(),
            req.getCongestionMultiplier(), req.getDescription()
        );
        disasterEngine.addDisaster(graph, event);
    }

    public void removeDisaster(String disasterId) {
        disasterEngine.removeDisaster(graph, disasterId);
    }

    public void clearAllDisasters() {
        disasterEngine.clearAllDisasters(graph);
    }

    public Collection<DisasterEvent> getActiveDisasters() {
        return disasterEngine.getActiveDisasters();
    }

    // ---- Graph Data for Frontend ----

    public Graph getGraph() {
        return graph;
    }

    public ShelterService getShelterService() {
        return shelterService;
    }

    public int getNodeCount() {
        return graph.getNodeCount();
    }

    public int getEdgeCount() {
        return graph.getEdgeCount();
    }
}
