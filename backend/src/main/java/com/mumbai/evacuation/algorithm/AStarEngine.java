package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * Classical A* Shortest Path Search Engine using Haversine Distance Heuristic.
 * 
 * Algorithm Specification (per system prompt requirements):
 * - Evaluates nodes based on f(n) = g(n) + h(n)
 *   - g(n) = actual dynamic travel time in seconds from source to node n.
 *   - h(n) = admissible Haversine travel time heuristic to target node.
 * - Significantly reduces search space (nodes explored) compared to uniform-cost Dijkstra.
 * - Tracks execution time (ns) and total nodes explored for benchmark comparison.
 */
public class AStarEngine {

    private static class NodeFScore implements Comparable<NodeFScore> {
        final long nodeId;
        final double fScore;

        NodeFScore(long nodeId, double fScore) {
            this.nodeId = nodeId;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(NodeFScore o) {
            return Double.compare(this.fScore, o.fScore);
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
        PriorityQueue<NodeFScore> openSet = new PriorityQueue<>();
        Set<Long> closedSet = new HashSet<>();

        gScore.put(sourceNodeId, 0.0);
        double initialH = Heuristic.calculateAdmissibleTravelTimeHeuristic(sourceNode, targetNode);
        openSet.add(new NodeFScore(sourceNodeId, initialH));

        int nodesExplored = 0;
        boolean found = false;

        while (!openSet.isEmpty()) {
            NodeFScore current = openSet.poll();
            long u = current.nodeId;

            if (closedSet.contains(u)) continue;
            closedSet.add(u);
            nodesExplored++;

            if (u == targetNodeId) {
                found = true;
                break;
            }

            double currentG = gScore.getOrDefault(u, Double.POSITIVE_INFINITY);
            Node currNode = graph.getNode(u);

            for (Edge edge : graph.getOutgoingEdges(u)) {
                if (edge.isBlocked()) continue; // Impassable road due to disaster

                long v = edge.getTargetNodeId();
                if (closedSet.contains(v)) continue;

                double travelTime = edge.getTravelTimeSeconds();
                if (Double.isInfinite(travelTime) || travelTime <= 0) continue;

                double tentativeG = currentG + travelTime;

                if (tentativeG < gScore.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    gScore.put(v, tentativeG);
                    parentEdgeMap.put(v, edge);

                    Node neighborNode = graph.getNode(v);
                    double h = Heuristic.calculateAdmissibleTravelTimeHeuristic(neighborNode, targetNode);
                    double fScore = tentativeG + h;

                    openSet.add(new NodeFScore(v, fScore));
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
