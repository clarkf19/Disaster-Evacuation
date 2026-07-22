package com.mumbai.evacuation.model;

import java.util.Collections;
import java.util.List;

/**
 * Result data DTO representing the calculated path between source and destination.
 */
public class PathResult {
    private final List<Node> pathNodes;
    private final List<Edge> pathEdges;
    private final double totalDistanceMeters;
    private final double totalTravelTimeSeconds;
    private final int nodesExplored;
    private final long executionTimeNs;
    private final boolean pathFound;

    public PathResult(List<Node> pathNodes, List<Edge> pathEdges, double totalDistanceMeters, 
                      double totalTravelTimeSeconds, int nodesExplored, long executionTimeNs, boolean pathFound) {
        this.pathNodes = pathNodes != null ? pathNodes : Collections.emptyList();
        this.pathEdges = pathEdges != null ? pathEdges : Collections.emptyList();
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalTravelTimeSeconds = totalTravelTimeSeconds;
        this.nodesExplored = nodesExplored;
        this.executionTimeNs = executionTimeNs;
        this.pathFound = pathFound;
    }

    public static PathResult emptyResult(int nodesExplored, long executionTimeNs) {
        return new PathResult(Collections.emptyList(), Collections.emptyList(), 0.0, 0.0, nodesExplored, executionTimeNs, false);
    }

    public List<Node> getPathNodes() {
        return pathNodes;
    }

    public List<Edge> getPathEdges() {
        return pathEdges;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public double getTotalTravelTimeSeconds() {
        return totalTravelTimeSeconds;
    }

    public double getTotalTravelTimeMinutes() {
        return totalTravelTimeSeconds / 60.0;
    }

    public int getNodesExplored() {
        return nodesExplored;
    }

    public long getExecutionTimeNs() {
        return executionTimeNs;
    }

    public double getExecutionTimeMs() {
        return executionTimeNs / 1_000_000.0;
    }

    public boolean isPathFound() {
        return pathFound;
    }

    @Override
    public String toString() {
        return "PathResult{" +
                "pathFound=" + pathFound +
                ", nodesCount=" + pathNodes.size() +
                ", dist=" + String.format("%.2f km", totalDistanceMeters / 1000.0) +
                ", travelTime=" + String.format("%.2f min", getTotalTravelTimeMinutes()) +
                ", nodesExplored=" + nodesExplored +
                ", execTime=" + String.format("%.3f ms", getExecutionTimeMs()) +
                '}';
    }
}
