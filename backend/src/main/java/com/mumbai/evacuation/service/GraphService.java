package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.disaster.DisasterEngine;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.loader.CsvGraphLoader;
import com.mumbai.evacuation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Owns the immutable road graph and the live disaster state, and answers
 * hazard-aware route queries.
 *
 * The graph is loaded from the classpath ({@code data/mumbai_*.csv}) by default;
 * set {@code graph.nodes-resource} / {@code graph.edges-resource} (e.g. to a
 * {@code file:} URL) to use a different extract. Startup fails loudly if the data
 * can't be loaded — an evacuation router with an empty graph is worse than none.
 */
@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    /** Origins/destinations farther than this from the road network are outside coverage. */
    public static final double MAX_SNAP_METERS = 2_000;

    private final Graph graph;
    private final DisasterEngine disasterEngine;
    private final AStarEngine aStarEngine = new AStarEngine();
    private final DijkstraEngine dijkstraEngine = new DijkstraEngine();

    public GraphService(ResourceLoader resourceLoader,
                        @Value("${graph.nodes-resource:classpath:data/mumbai_nodes.csv}") String nodesResource,
                        @Value("${graph.edges-resource:classpath:data/mumbai_edges.csv}") String edgesResource) {
        try (InputStream nodes = resourceLoader.getResource(nodesResource).getInputStream();
             InputStream edges = resourceLoader.getResource(edgesResource).getInputStream()) {
            this.graph = CsvGraphLoader.load(nodes, edges);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load road graph from " + nodesResource + " / " + edgesResource, e);
        }
        this.disasterEngine = new DisasterEngine(graph);
        log.info("Loaded road graph: {} nodes, {} edges, bounds {}", graph.getNodeCount(), graph.getEdgeCount(),
                Arrays.toString(graph.getBounds()));
    }

    public Graph getGraph() { return graph; }

    // ---- Snapping ----

    public record Snap(Node node, double distanceMeters) {
        public boolean withinCoverage() { return node != null && distanceMeters <= MAX_SNAP_METERS; }
    }

    public Snap snap(double lat, double lon) {
        Node node = graph.findNearestNode(lat, lon);
        double d = node == null ? Double.POSITIVE_INFINITY
                : GeoUtils.haversineMeters(lat, lon, node.getLatitude(), node.getLongitude());
        return new Snap(node, d);
    }

    // ---- Disasters ----

    public DisasterEvent addDisaster(DisasterRequest req) {
        String id = req.id() == null || req.id().isBlank() ? "disaster-" + UUID.randomUUID() : req.id();
        DisasterEvent event = toEvent(id, req);
        disasterEngine.addDisaster(event);
        log.info("Disaster {} added: {} r={}m blockRoads={}", id, req.type(), req.radiusMeters(), req.blockRoads());
        return event;
    }

    public static DisasterEvent toEvent(String id, DisasterRequest req) {
        double multiplier = req.congestionMultiplier() == null ? 3.0 : req.congestionMultiplier();
        String description = req.description() == null || req.description().isBlank()
                ? req.type() + " hazard" : req.description();
        return new DisasterEvent(id, req.type(), req.latitude(), req.longitude(), req.radiusMeters(),
                req.blockRoads(), multiplier, description);
    }

    public boolean removeDisaster(String disasterId) { return disasterEngine.removeDisaster(disasterId); }
    public void clearAllDisasters() { disasterEngine.clearAllDisasters(); }
    public List<DisasterEvent> getActiveDisasters() { return disasterEngine.getActiveDisasters(); }
    public HazardOverlay getHazardOverlay() { return disasterEngine.getOverlay(); }

    // ---- Routing ----

    public PathResult shortestPath(long sourceNodeId, long targetNodeId, String algorithm, EdgeCost cost) {
        return "DIJKSTRA".equalsIgnoreCase(algorithm)
                ? dijkstraEngine.findShortestPath(graph, cost, sourceNodeId, targetNodeId)
                : aStarEngine.findShortestPath(graph, cost, sourceNodeId, targetNodeId);
    }

    /** Node-to-node route under the live hazard overlay. */
    public RouteResponse computeRoute(long sourceNodeId, long targetNodeId, String algorithm) {
        if (graph.getNode(sourceNodeId) == null || graph.getNode(targetNodeId) == null) {
            throw new NoSuchElementException("Unknown node id");
        }
        String algo = "DIJKSTRA".equalsIgnoreCase(algorithm) ? "DIJKSTRA" : "ASTAR";
        HazardOverlay overlay = disasterEngine.getOverlay();
        PathResult result = shortestPath(sourceNodeId, targetNodeId, algo, overlay.asEdgeCost());
        return buildRouteResponse(result, sourceNodeId, targetNodeId, algo, overlay);
    }

    /** Point-to-point route: snaps both points to the network, then routes under the live hazard overlay. */
    public RouteResponse computeRoute(double fromLat, double fromLon, double toLat, double toLon) {
        Snap from = snap(fromLat, fromLon);
        Snap to = snap(toLat, toLon);
        HazardOverlay overlay = disasterEngine.getOverlay();
        RouteResponse response;
        if (!from.withinCoverage() || !to.withinCoverage()) {
            response = new RouteResponse();
            response.setPathFound(false);
            response.setLiveRouteStatus("OUT_OF_COVERAGE");
            response.setLiveAdvisoryMessage("Start or destination is more than "
                    + (int) (MAX_SNAP_METERS / 1000) + " km from the mapped road network. Coverage: Greater Mumbai up to Thane.");
        } else {
            PathResult result = shortestPath(from.node().getId(), to.node().getId(), "ASTAR", overlay.asEdgeCost());
            response = buildRouteResponse(result, from.node().getId(), to.node().getId(), "ASTAR", overlay);
        }
        response.setSourceSnapMeters(from.distanceMeters());
        response.setTargetSnapMeters(to.distanceMeters());
        return response;
    }

    private RouteResponse buildRouteResponse(PathResult result, long srcId, long dstId, String algo, HazardOverlay overlay) {
        RouteResponse response = new RouteResponse();
        response.setPathFound(result.isPathFound());
        response.setSourceNodeId(srcId);
        response.setTargetNodeId(dstId);
        response.setNodesExplored(result.getNodesExplored());
        response.setExecutionTimeMs(result.getExecutionTimeMs());
        response.setAlgorithmUsed(algo);

        if (!result.isPathFound()) {
            response.setLiveRouteStatus("UNPASSABLE");
            response.setLiveAdvisoryMessage("No passable route: every road to the destination is blocked by an active hazard. "
                    + "Choose another destination or shelter.");
            return response;
        }

        response.setTotalDistanceKm(result.getTotalDistanceMeters() / 1000.0);
        response.setTotalTravelTimeMinutes(result.getTotalTravelTimeMinutes());

        List<double[]> coords = new ArrayList<>();
        for (Node node : result.getPathNodes()) coords.add(new double[]{node.getLatitude(), node.getLongitude()});
        response.setRawCoordinates(coords);

        double freeFlowSeconds = 0;
        boolean usesEgress = false;
        List<RouteResponse.SegmentDetail> segments = new ArrayList<>();
        for (Edge edge : result.getPathEdges()) {
            Node a = graph.getNode(edge.getSourceNodeId());
            Node b = graph.getNode(edge.getTargetNodeId());
            double factor = overlay.multiplier(edge);
            if (factor == HazardOverlay.EGRESS_PENALTY) usesEgress = true;
            segments.add(new RouteResponse.SegmentDetail(a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude(), factor, edge.getRoadType()));
            freeFlowSeconds += edge.getFreeFlowSeconds();
        }
        response.setSegmentDetails(segments);

        double freeFlowMins = freeFlowSeconds / 60.0;
        double delayMins = Math.max(0.0, result.getTotalTravelTimeMinutes() - freeFlowMins);
        response.setFreeFlowTravelTimeMinutes(freeFlowMins);
        response.setCongestionDelayMinutes(delayMins);

        // Did the hazards actually change the route? Compare with the hazard-free optimum.
        boolean detoured = false;
        if (!overlay.isEmpty()) {
            PathResult unconstrained = aStarEngine.findShortestPath(graph, EdgeCost.FREE_FLOW, srcId, dstId);
            detoured = unconstrained.isPathFound()
                    && unconstrained.getPathEdges().stream().anyMatch(e -> overlay.isBlocked(e) || overlay.multiplier(e) > 1.0)
                    && !sameEdges(unconstrained, result);
        }

        if (usesEgress) {
            response.setLiveRouteStatus("HAZARD_EGRESS");
            response.setLiveAdvisoryMessage("You are inside an active hazard zone. Follow this route OUT of the zone — "
                    + "expect slow movement for the first stretch. Do not re-enter the zone.");
        } else if (detoured) {
            response.setLiveRouteStatus("DISASTER_BYPASS");
            response.setLiveAdvisoryMessage(String.format(Locale.US,
                    "Route diverted around active hazard zones (+%d min vs. the normal route).",
                    Math.round(delayMins)));
        } else if (delayMins >= 4.0) {
            response.setLiveRouteStatus("HEAVY_CONGESTION");
            response.setLiveAdvisoryMessage(String.format(Locale.US, "Hazard-related congestion on this route (+%d min).", Math.round(delayMins)));
        } else if (delayMins >= 1.0) {
            response.setLiveRouteStatus("MODERATE_TRAFFIC");
            response.setLiveAdvisoryMessage(String.format(Locale.US, "Some hazard-related slow-down (+%d min).", Math.round(delayMins)));
        } else {
            response.setLiveRouteStatus("CLEAR");
            response.setLiveAdvisoryMessage("No active hazards affect this route. Times assume free-flowing traffic (no live traffic data).");
        }
        return response;
    }

    private static boolean sameEdges(PathResult a, PathResult b) {
        if (a.getPathEdges().size() != b.getPathEdges().size()) return false;
        for (int i = 0; i < a.getPathEdges().size(); i++) {
            if (a.getPathEdges().get(i).getId() != b.getPathEdges().get(i).getId()) return false;
        }
        return true;
    }
}
