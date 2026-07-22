package com.mumbai.evacuation.dto;

public class RouteRequest {
    private long sourceNodeId;
    private long targetNodeId;
    private String algorithm = "ASTAR"; // "DIJKSTRA" or "ASTAR"

    public RouteRequest() {}

    public RouteRequest(long sourceNodeId, long targetNodeId, String algorithm) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.algorithm = algorithm;
    }

    public long getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(long sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public long getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(long targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
