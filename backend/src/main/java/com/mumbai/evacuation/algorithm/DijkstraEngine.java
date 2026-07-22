package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * Classical Dijkstra Shortest Path Search Engine for Mumbai Road Network.
 * 
 * Algorithm Specification (per system prompt requirements):
 * - Uses a PriorityQueue to expand node with minimum tentative travel time.
 * - Maintains a distance map (min travel time in seconds to reach node) and parent edge map.
 * - Respects edge blockages and dynamic congestion factors.
 * - Explicitly tracks execution time (ns) and total nodes explored for benchmark comparison against A*.
 */
public class DijkstraEngine {

    private static class NodeDistance implements Comparable<NodeDistance> {
        final long nodeId;
        final double distance;

        NodeDistance(long nodeId, double distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance o) {
            return Double.compare(this.distance, o.distance);
        }
    }

    public PathResult findShortestPath(Graph graph, long sourceNodeId, long targetNodeId) {
        long startTime = System.nanoTime();
        
        Node sourceNode = graph.getNode(sourceNodeId);
        Node targetNode = graph.getNode(targetNodeId);
        if (sourceNode == null || targetNode == null) {
            return PathResult.emptyResult(0, System.nanoTime() - startTime);
        }

        if (sourceNodeId == targetNodeId) {
            return new PathResult(Collections.singletonList(sourceNode), Collections.emptyList(), 0.0, 0.0, 1, System.nanoTime() - startTime, true);
        }

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Edge> parentEdgeMap = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>();
        Set<Long> visited = new HashSet<>();

        gScore.put(sourceNodeId, 0.0);
        pq.add(new NodeDistance(sourceNodeId, 0.0));

        int nodesExplored = 0;
        boolean found = false;

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            long u = current.nodeId;

            if (visited.contains(u)) continue;
            visited.add(u);
            nodesExplored++;

            if (u == targetNodeId) {
                found = true;
                break;
            }

            double currentDist = gScore.getOrDefault(u, Double.POSITIVE_INFINITY);

            for (Edge edge : graph.getOutgoingEdges(u)) {
                if (edge.isBlocked()) continue; // Impassable road due to disaster

                long v = edge.getTargetNodeId();
                if (visited.contains(v)) continue;

                double travelTime = edge.getTravelTimeSeconds();
                if (Double.isInfinite(travelTime) || travelTime <= 0) continue;

                double tentativeG = currentDist + travelTime;

                if (tentativeG < gScore.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    gScore.put(v, tentativeG);
                    parentEdgeMap.put(v, edge);
                    pq.add(new NodeDistance(v, tentativeG));
                }
            }
        }

        long endTime = System.nanoTime();
        long executionTimeNs = endTime - startTime;

        if (!found) {
            return PathResult.emptyResult(nodesExplored, executionTimeNs);
        }

        // Reconstruct path
        LinkedList<Node> pathNodes = new LinkedList<>();
        LinkedList<Edge> pathEdges = new LinkedList<>();
        long curr = targetNodeId;
        double totalDistMeters = 0.0;

        pathNodes.addFirst(graph.getNode(curr));
        while (curr != sourceNodeId) {
            Edge edge = parentEdgeMap.get(curr);
            if (edge == null) break;
            pathEdges.addFirst(edge);
            totalDistMeters += edge.getDistanceMeters();
            curr = edge.getSourceNodeId();
            pathNodes.addFirst(graph.getNode(curr));
        }

        double totalTravelTimeSeconds = gScore.get(targetNodeId);

        return new PathResult(pathNodes, pathEdges, totalDistMeters, totalTravelTimeSeconds, nodesExplored, executionTimeNs, true);
    }
}
