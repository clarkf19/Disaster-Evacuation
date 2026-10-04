package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * A* search over travel time with an admissible straight-line heuristic
 * (see {@link Heuristic}). Returns the same optimal cost as Dijkstra while
 * exploring far fewer nodes on long corridors.
 */
public class AStarEngine {

    private record QueueEntry(long nodeId, double fScore) {}

    /** Driving search: the heuristic uses the fastest road speed limit. */
    public PathResult findShortestPath(Graph graph, EdgeCost cost, long sourceNodeId, long targetNodeId) {
        return findShortestPath(graph, cost, sourceNodeId, targetNodeId, graph.getMaxRoadSpeedKmH());
    }

    /**
     * @param maxSpeedKmH an upper bound on speed for the mode being searched; every
     *                    finite edge cost must be >= distance / maxSpeed (admissibility)
     */
    public PathResult findShortestPath(Graph graph, EdgeCost cost, long sourceNodeId, long targetNodeId, double maxSpeedKmH) {
        long start = System.nanoTime();
        Node targetNode = graph.getNode(targetNodeId);
        if (graph.getNode(sourceNodeId) == null || targetNode == null) {
            return PathResult.emptyResult(0, System.nanoTime() - start);
        }
        double maxSpeed = maxSpeedKmH;

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Edge> parent = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::fScore));

        gScore.put(sourceNodeId, 0.0);
        open.add(new QueueEntry(sourceNodeId,
                Heuristic.travelTimeLowerBoundSeconds(graph.getNode(sourceNodeId), targetNode, maxSpeed)));

        while (!open.isEmpty()) {
            long u = open.poll().nodeId();
            if (!closed.add(u)) continue;
            if (u == targetNodeId) {
                return new ShortestPathTree(graph, sourceNodeId, gScore, parent, closed, closed.size(),
                        System.nanoTime() - start).pathTo(targetNodeId);
            }
            double g = gScore.get(u);
            for (Edge edge : graph.getOutgoingEdges(u)) {
                long v = edge.getTargetNodeId();
                if (closed.contains(v)) continue;
                double edgeCost = cost.seconds(edge);
                if (!Double.isFinite(edgeCost)) continue;
                double tentative = g + edgeCost;
                if (tentative < gScore.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    gScore.put(v, tentative);
                    parent.put(v, edge);
                    double h = Heuristic.travelTimeLowerBoundSeconds(graph.getNode(v), targetNode, maxSpeed);
                    open.add(new QueueEntry(v, tentative + h));
                }
            }
        }
        return PathResult.emptyResult(closed.size(), System.nanoTime() - start);
    }
}
