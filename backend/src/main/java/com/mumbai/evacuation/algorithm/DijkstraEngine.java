package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * Dijkstra shortest-path search over travel time.
 *
 * Besides the classic single-pair query, {@link #searchToTargets} runs one search
 * that stops as soon as every target is settled. The evacuation engine uses it
 * to get travel times from a group to every shelter in one pass instead of one
 * A* run per shelter.
 */
public class DijkstraEngine {

    private record QueueEntry(long nodeId, double cost) {}

    public PathResult findShortestPath(Graph graph, EdgeCost cost, long sourceNodeId, long targetNodeId) {
        if (graph.getNode(sourceNodeId) == null || graph.getNode(targetNodeId) == null) {
            return PathResult.emptyResult(0, 0);
        }
        return searchToTargets(graph, cost, sourceNodeId, Set.of(targetNodeId)).pathTo(targetNodeId);
    }

    /**
     * Single-source search that terminates once all {@code targets} are settled
     * (or the reachable graph is exhausted).
     */
    public ShortestPathTree searchToTargets(Graph graph, EdgeCost cost, long sourceNodeId, Set<Long> targets) {
        return searchToTargets(graph, cost, sourceNodeId, targets, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #searchToTargets(Graph, EdgeCost, long, Set)} but stops once
     * {@code maxTargets} targets are settled. Dijkstra settles nodes in order of
     * cost, so the first k settled targets are exactly the k nearest — e.g.
     * {@code maxTargets = 1} finds the nearest shelter without exploring the city.
     */
    public ShortestPathTree searchToTargets(Graph graph, EdgeCost cost, long sourceNodeId, Set<Long> targets, int maxTargets) {
        long start = System.nanoTime();
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Edge> parent = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::cost));

        if (graph.getNode(sourceNodeId) != null) {
            best.put(sourceNodeId, 0.0);
            queue.add(new QueueEntry(sourceNodeId, 0.0));
        }
        Set<Long> remaining = new HashSet<>(targets);
        int found = 0;

        while (!queue.isEmpty() && !remaining.isEmpty() && found < maxTargets) {
            QueueEntry current = queue.poll();
            long u = current.nodeId();
            if (!settled.add(u)) continue;
            if (remaining.remove(u)) {
                found++;
                if (found >= maxTargets) break;
            }

            for (Edge edge : graph.getOutgoingEdges(u)) {
                long v = edge.getTargetNodeId();
                if (settled.contains(v)) continue;
                double edgeCost = cost.seconds(edge);
                if (!Double.isFinite(edgeCost)) continue;
                double candidate = current.cost() + edgeCost;
                if (candidate < best.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    best.put(v, candidate);
                    parent.put(v, edge);
                    queue.add(new QueueEntry(v, candidate));
                }
            }
        }
        return new ShortestPathTree(graph, sourceNodeId, best, parent, settled, settled.size(), System.nanoTime() - start);
    }
}
