package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * Result of a single-source search: best known cost to every settled node plus
 * the parent edge used to reach it, so paths to many targets can be rebuilt
 * from one search.
 */
public class ShortestPathTree {
    private final Graph graph;
    private final long sourceNodeId;
    private final Map<Long, Double> costSeconds;
    private final Map<Long, Edge> parentEdge;
    private final Set<Long> settled;
    private final int nodesExplored;
    private final long executionTimeNs;

    ShortestPathTree(Graph graph, long sourceNodeId, Map<Long, Double> costSeconds, Map<Long, Edge> parentEdge,
                     Set<Long> settled, int nodesExplored, long executionTimeNs) {
        this.graph = graph;
        this.sourceNodeId = sourceNodeId;
        this.costSeconds = costSeconds;
        this.parentEdge = parentEdge;
        this.settled = settled;
        this.nodesExplored = nodesExplored;
        this.executionTimeNs = executionTimeNs;
    }

    public boolean reached(long nodeId) { return settled.contains(nodeId); }

    /** Optimal cost in seconds to a settled node, or +infinity. */
    public double costTo(long nodeId) {
        return settled.contains(nodeId) ? costSeconds.get(nodeId) : Double.POSITIVE_INFINITY;
    }

    public int getNodesExplored() { return nodesExplored; }
    public long getExecutionTimeNs() { return executionTimeNs; }

    public PathResult pathTo(long targetNodeId) {
        if (!reached(targetNodeId)) return PathResult.emptyResult(nodesExplored, executionTimeNs);
        LinkedList<Node> nodes = new LinkedList<>();
        LinkedList<Edge> edges = new LinkedList<>();
        double distance = 0.0;
        long current = targetNodeId;
        nodes.addFirst(graph.getNode(current));
        while (current != sourceNodeId) {
            Edge edge = parentEdge.get(current);
            edges.addFirst(edge);
            distance += edge.getDistanceMeters();
            current = edge.getSourceNodeId();
            nodes.addFirst(graph.getNode(current));
        }
        return new PathResult(nodes, edges, distance, costSeconds.get(targetNodeId), nodesExplored, executionTimeNs, true);
    }
}
