package com.mumbai.evacuation.disaster;

import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Heuristic;
import com.mumbai.evacuation.model.Node;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Disaster Impact Engine.
 * 
 * Design Decision & Trade-off Rationale:
 * 1. Road Mutation:
 *    - Applies disasters by calculating the Haversine distance from the disaster epicenter
 *      to road edge midpoints/nodes.
 *    - If inside affected radius and blockRoads == true, sets edge.setBlocked(true).
 *    - If inside affected radius and blockRoads == false, sets edge.setCustomCongestionMultiplier(multiplier).
 * 2. Recomputation Policy:
 *    - Upon disaster injection or clear, pathfinding is re-run FROM SCRATCH (using Dijkstra/A*)
 *      rather than using incremental algorithms like D* Lite.
 *    - Trade-off Justification: Full recomputation on an in-memory graph (~8,000 nodes) takes <25ms,
 *      eliminating the algorithmic complexity and state consistency bugs of D* Lite while providing
 *      100% correct, explainable, global optimal routes.
 */
public class DisasterEngine {

    private final Map<String, DisasterEvent> activeDisasters = new ConcurrentHashMap<>();

    public void addDisaster(Graph graph, DisasterEvent event) {
        activeDisasters.put(event.getId(), event);
        applyDisastersToGraph(graph);
    }

    public void removeDisaster(Graph graph, String disasterId) {
        activeDisasters.remove(disasterId);
        applyDisastersToGraph(graph);
    }

    public void clearAllDisasters(Graph graph) {
        activeDisasters.clear();
        applyDisastersToGraph(graph);
    }

    public Collection<DisasterEvent> getActiveDisasters() {
        return activeDisasters.values();
    }

    /**
     * Mutates the edges of the graph based on active disaster events.
     */
    public synchronized void applyDisastersToGraph(Graph graph) {
        // Reset all edges to default state
        for (Edge edge : graph.getAllEdges()) {
            edge.setBlocked(false);
            edge.setCustomCongestionMultiplier(1.0);
            edge.setCurrentTraffic(0);
        }

        // Apply each active disaster
        for (DisasterEvent disaster : activeDisasters.values()) {
            Node epicenter = new Node(-1, disaster.getCenterLatitude(), disaster.getCenterLongitude());

            for (Edge edge : graph.getAllEdges()) {
                Node source = graph.getNode(edge.getSourceNodeId());
                Node target = graph.getNode(edge.getTargetNodeId());
                if (source == null || target == null) continue;

                // Midpoint of road edge
                double midLat = (source.getLatitude() + target.getLatitude()) / 2.0;
                double midLon = (source.getLongitude() + target.getLongitude()) / 2.0;
                Node midNode = new Node(-2, midLat, midLon);

                double distToEpicenterMeters = Heuristic.haversineDistanceMeters(midNode, epicenter);

                if (distToEpicenterMeters <= disaster.getAffectedRadiusMeters()) {
                    if (disaster.isBlockRoads()) {
                        edge.setBlocked(true);
                    } else {
                        edge.setCustomCongestionMultiplier(
                            Math.max(edge.getCustomCongestionMultiplier(), disaster.getCongestionMultiplier())
                        );
                    }
                }
            }
        }
    }
}
