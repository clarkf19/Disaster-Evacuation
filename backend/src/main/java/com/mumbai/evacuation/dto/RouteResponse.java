package com.mumbai.evacuation.dto;

import java.util.List;

public class RouteResponse {
    private boolean pathFound;
    private long sourceNodeId;
    private long targetNodeId;
    private double totalDistanceKm;
    private double totalTravelTimeMinutes;
    private int nodesExplored;
    private double executionTimeMs;
    private String algorithmUsed;
    private List<double[]> rawCoordinates; // [lat, lon] pairs as doubles

    public RouteResponse() {}

    public boolean isPathFound() { return pathFound; }
    public void setPathFound(boolean pathFound) { this.pathFound = pathFound; }

    public long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(long sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public long getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(long targetNodeId) { this.targetNodeId = targetNodeId; }

    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public double getTotalTravelTimeMinutes() { return totalTravelTimeMinutes; }
    public void setTotalTravelTimeMinutes(double totalTravelTimeMinutes) { this.totalTravelTimeMinutes = totalTravelTimeMinutes; }

    public int getNodesExplored() { return nodesExplored; }
    public void setNodesExplored(int nodesExplored) { this.nodesExplored = nodesExplored; }

    public double getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(double executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getAlgorithmUsed() { return algorithmUsed; }
    public void setAlgorithmUsed(String algorithmUsed) { this.algorithmUsed = algorithmUsed; }

    public List<double[]> getRawCoordinates() { return rawCoordinates; }
    public void setRawCoordinates(List<double[]> rawCoordinates) { this.rawCoordinates = rawCoordinates; }
}
