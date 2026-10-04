package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.CostModel;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.disaster.DisasterEngine;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.loader.CsvGraphLoader;
import com.mumbai.evacuation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Owns the immutable multimodal network and the live disaster state, and
 * answers hazard-aware route queries for driving, walking and train + walk.
 *
 * Data is loaded from the classpath ({@code data/*.csv}) by default; the
 * {@code graph.*-resource} properties can point elsewhere (e.g. {@code file:}).
 * The railway files are optional. Startup fails loudly if the road data can't
 * be loaded — an evacuation router with an empty graph is worse than none.
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
                        @Value("${graph.edges-resource:classpath:data/mumbai_edges.csv}") String edgesResource,
                        @Value("${graph.stations-resource:classpath:data/rail_stations.csv}") String stationsResource,
                        @Value("${graph.rail-links-resource:classpath:data/rail_links.csv}") String railLinksResource) {
        Resource stations = resourceLoader.getResource(stationsResource);
        Resource links = resourceLoader.getResource(railLinksResource);
        boolean withRail = stations.exists() && links.exists();
        try (InputStream nodes = resourceLoader.getResource(nodesResource).getInputStream();
             InputStream edges = resourceLoader.getResource(edgesResource).getInputStream();
             InputStream stationIn = withRail ? stations.getInputStream() : null;
             InputStream linkIn = withRail ? links.getInputStream() : null) {
            this.graph = CsvGraphLoader.load(nodes, edges, stationIn, linkIn);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load road graph from " + nodesResource + " / " + edgesResource, e);
        }
        this.disasterEngine = new DisasterEngine(graph);
        log.info("Loaded network: {} road nodes, {} road edges, {} walk-only reverse edges, {} stations, {} rail edges; bounds {}",
                graph.getRoadNodeCount(), graph.getEdgeCount(Edge.Kind.ROAD), graph.getEdgeCount(Edge.Kind.ROAD_REVERSE),
                graph.getStations().size(), graph.getEdgeCount(Edge.Kind.RAIL), Arrays.toString(graph.getBounds()));
        if (!withRail) log.warn("Railway data not found — TRANSIT mode will fall back to walking.");
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

    public void addDisasters(List<DisasterEvent> events) {
        disasterEngine.addDisasters(events);
        log.info("{} disasters added in bulk", events.size());
    }

    public boolean removeDisaster(String disasterId) { return disasterEngine.removeDisaster(disasterId); }
    public void clearAllDisasters() { disasterEngine.clearAllDisasters(); }
    public List<DisasterEvent> getActiveDisasters() { return disasterEngine.getActiveDisasters(); }
    public HazardOverlay getHazardOverlay() { return disasterEngine.getOverlay(); }

    // ---- Routing ----

    /** Driving shortest path with the given algorithm (ASTAR default, or DIJKSTRA). */
    public PathResult shortestPath(long sourceNodeId, long targetNodeId, String algorithm, EdgeCost cost) {
        return "DIJKSTRA".equalsIgnoreCase(algorithm)
                ? dijkstraEngine.findShortestPath(graph, cost, sourceNodeId, targetNodeId)
                : aStarEngine.findShortestPath(graph, cost, sourceNodeId, targetNodeId);
    }

    /** Shortest path in any travel mode under the given overlay (A*). */
    public PathResult shortestPath(long sourceNodeId, long targetNodeId, TravelMode mode, HazardOverlay overlay) {
        return aStarEngine.findShortestPath(graph, CostModel.of(mode, overlay), sourceNodeId, targetNodeId,
                mode.maxSpeedKmH(graph));
    }

    /** Node-to-node driving route under the live hazard overlay. */
    public RouteResponse computeRoute(long sourceNodeId, long targetNodeId, String algorithm) {
        if (graph.getNode(sourceNodeId) == null || graph.getNode(targetNodeId) == null) {
            throw new NoSuchElementException("Unknown node id");
        }
        String algo = "DIJKSTRA".equalsIgnoreCase(algorithm) ? "DIJKSTRA" : "ASTAR";
        HazardOverlay overlay = disasterEngine.getOverlay();
        PathResult result = shortestPath(sourceNodeId, targetNodeId, algo, overlay.asEdgeCost());
        return buildRouteResponse(result, sourceNodeId, targetNodeId, algo, overlay, TravelMode.DRIVE);
    }

    /** Point-to-point driving route. */
    public RouteResponse computeRoute(double fromLat, double fromLon, double toLat, double toLon) {
        return computeRoute(fromLat, fromLon, toLat, toLon, TravelMode.DRIVE);
    }

    /** Point-to-point route in the given mode: snaps both points to the road network, then routes. */
    public RouteResponse computeRoute(double fromLat, double fromLon, double toLat, double toLon, TravelMode mode) {
        Snap from = snap(fromLat, fromLon);
        Snap to = snap(toLat, toLon);
        HazardOverlay overlay = disasterEngine.getOverlay();
        RouteResponse response;
        if (!from.withinCoverage() || !to.withinCoverage()) {
            response = new RouteResponse();
            response.setPathFound(false);
            response.setTravelMode(mode.name());
            response.setLiveRouteStatus("OUT_OF_COVERAGE");
            double[] b = graph.getBounds();
            response.setLiveAdvisoryMessage(String.format(Locale.US,
                    "Start or destination is more than %d km from the mapped road network (covers lat %.2f–%.2f, lon %.2f–%.2f).",
                    (int) (MAX_SNAP_METERS / 1000), b[0], b[2], b[1], b[3]));
        } else {
            PathResult result = shortestPath(from.node().getId(), to.node().getId(), mode, overlay);
            response = buildRouteResponse(result, from.node().getId(), to.node().getId(), "ASTAR", overlay, mode);
        }
        response.setSourceSnapMeters(from.distanceMeters());
        response.setTargetSnapMeters(to.distanceMeters());
        return response;
    }

    private RouteResponse buildRouteResponse(PathResult result, long srcId, long dstId, String algo,
                                             HazardOverlay overlay, TravelMode mode) {
        RouteResponse response = new RouteResponse();
        response.setTravelMode(mode.name());
        response.setPathFound(result.isPathFound());
        response.setSourceNodeId(srcId);
        response.setTargetNodeId(dstId);
        response.setNodesExplored(result.getNodesExplored());
        response.setExecutionTimeMs(result.getExecutionTimeMs());
        response.setAlgorithmUsed(algo);

        if (!result.isPathFound()) {
            response.setLiveRouteStatus("UNPASSABLE");
            response.setLiveAdvisoryMessage("No passable route: every way to the destination is blocked by an active hazard. "
                    + "Choose another destination or shelter.");
            return response;
        }

        response.setTotalDistanceKm(result.getTotalDistanceMeters() / 1000.0);
        response.setTotalTravelTimeMinutes(result.getTotalTravelTimeMinutes());

        List<double[]> coords = new ArrayList<>();
        double lowest = Double.POSITIVE_INFINITY;
        for (Node node : result.getPathNodes()) {
            coords.add(new double[]{node.getLatitude(), node.getLongitude()});
            if (node.hasElevation() && !node.isStation()) lowest = Math.min(lowest, node.getElevationM());
        }
        response.setRawCoordinates(coords);
        if (Double.isFinite(lowest)) response.setLowestElevationM(Math.round(lowest * 10) / 10.0);

        EdgeCost liveCost = CostModel.of(mode, overlay);
        EdgeCost baseCost = CostModel.of(mode, HazardOverlay.empty());
        double baseSeconds = 0;
        boolean usesEgress = false;
        List<RouteResponse.SegmentDetail> segments = new ArrayList<>();
        for (Edge edge : result.getPathEdges()) {
            Node a = graph.getNode(edge.getSourceNodeId());
            Node b = graph.getNode(edge.getTargetNodeId());
            if (overlay.isEgress(edge)) usesEgress = true;
            segments.add(new RouteResponse.SegmentDetail(a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude(), overlay.multiplier(edge), edge.getRoadType(), segmentKind(edge, mode)));
            baseSeconds += baseCost.seconds(edge);
        }
        response.setSegmentDetails(segments);
        response.setLegs(buildLegs(result.getPathEdges(), mode, liveCost));

        double baseMins = baseSeconds / 60.0;
        double delayMins = Math.max(0.0, result.getTotalTravelTimeMinutes() - baseMins);
        response.setFreeFlowTravelTimeMinutes(baseMins);
        response.setCongestionDelayMinutes(delayMins);

        // Did the hazards actually change the route? Compare with the hazard-free optimum.
        boolean detoured = false;
        if (!overlay.isEmpty()) {
            PathResult unconstrained = shortestPath(srcId, dstId, mode, HazardOverlay.empty());
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
                    "Route diverted around active hazards and water-logged low ground (+%d min vs. the normal route).",
                    Math.round(delayMins)));
        } else if (delayMins >= 4.0) {
            response.setLiveRouteStatus("HEAVY_CONGESTION");
            response.setLiveAdvisoryMessage(String.format(Locale.US, "Hazard-related slow-down on this route (+%d min).", Math.round(delayMins)));
        } else if (delayMins >= 1.0) {
            response.setLiveRouteStatus("MODERATE_TRAFFIC");
            response.setLiveAdvisoryMessage(String.format(Locale.US, "Some hazard-related slow-down (+%d min).", Math.round(delayMins)));
        } else {
            response.setLiveRouteStatus("CLEAR");
            response.setLiveAdvisoryMessage(mode == TravelMode.DRIVE
                    ? "No active hazards affect this route. Times assume free-flowing traffic (no live traffic data)."
                    : "No active hazards affect this route.");
        }
        return response;
    }

    private static String segmentKind(Edge edge, TravelMode mode) {
        if (edge.getKind() == Edge.Kind.RAIL) return "rail";
        if (edge.getKind() == Edge.Kind.TRANSFER) return "walk";
        return mode == TravelMode.DRIVE ? "drive" : "walk";
    }

    /** Collapses consecutive edges into itinerary legs (walk / drive / wait / train per line). */
    private List<RouteResponse.Leg> buildLegs(List<Edge> edges, TravelMode mode, EdgeCost cost) {
        List<RouteResponse.Leg> legs = new ArrayList<>();
        String type = null, line = null, from = null, to = null;
        double seconds = 0, meters = 0;
        int stops = 0;
        for (Edge e : edges) {
            String t;
            String l = null;
            boolean boarding = e.getKind() == Edge.Kind.TRANSFER && graph.getNode(e.getTargetNodeId()).isStation();
            if (e.getKind() == Edge.Kind.RAIL) { t = "TRAIN"; l = e.getLine(); }
            else if (boarding) t = "WAIT";
            else t = mode == TravelMode.DRIVE ? "DRIVE" : "WALK";

            if (type != null && (!type.equals(t) || !Objects.equals(line, l))) {
                legs.add(leg(type, line, from, to, seconds, meters, stops));
                seconds = 0; meters = 0; stops = 0; from = null;
            }
            type = t;
            line = l;
            if (t.equals("TRAIN")) {
                if (from == null) from = graph.getNode(e.getSourceNodeId()).getName();
                to = graph.getNode(e.getTargetNodeId()).getName();
                stops++;
            } else if (t.equals("WAIT")) {
                from = graph.getNode(e.getTargetNodeId()).getName();
                to = from;
            }
            seconds += cost.seconds(e);
            if (!t.equals("WAIT")) meters += e.getDistanceMeters();
        }
        if (type != null) legs.add(leg(type, line, from, to, seconds, meters, stops));
        return legs;
    }

    private static RouteResponse.Leg leg(String type, String line, String from, String to, double seconds, double meters, int stops) {
        return new RouteResponse.Leg(type, line, from, to, Math.round(seconds / 6.0) / 10.0,
                Math.round(meters / 10.0) / 100.0, stops);
    }

    private static boolean sameEdges(PathResult a, PathResult b) {
        if (a.getPathEdges().size() != b.getPathEdges().size()) return false;
        for (int i = 0; i < a.getPathEdges().size(); i++) {
            if (a.getPathEdges().get(i).getId() != b.getPathEdges().get(i).getId()) return false;
        }
        return true;
    }

    /** True when any active disaster is a flood (used for low-ground warnings). */
    public boolean isFloodActive() {
        return getActiveDisasters().stream().anyMatch(d -> d.getType() == DisasterType.FLOOD);
    }
}
