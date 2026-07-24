package com.mumbai.evacuation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing real-time computed route results
 * accounting for live road congestion, active disasters, and traffic delays.
 */
public class RouteResponse {
    private boolean pathFound;
    private long sourceNodeId;
    private long targetNodeId;
    private double totalDistanceKm;
    private double totalTravelTimeMinutes;
    private double freeFlowTravelTimeMinutes;
    private double congestionDelayMinutes;
    private String liveRouteStatus; // CLEAR, MODERATE_TRAFFIC, HEAVY_CONGESTION, DISASTER_BYPASS
    private String liveAdvisoryMessage;
    private int nodesExplored;
    private double executionTimeMs;
    private String algorithmUsed;
    private List<double[]> rawCoordinates; // [lat, lon] pairs
    private List<SegmentDetail> segmentDetails = new ArrayList<>();

    public static class SegmentDetail {
        private double startLat;
        private double startLon;
        private double endLat;
        private double endLon;
        private double congestionFactor;
        private String roadType;

        public SegmentDetail(double startLat, double startLon, double endLat, double endLon, double congestionFactor, String roadType) {
            this.startLat = startLat;
            this.startLon = startLon;
            this.endLat = endLat;
            this.endLon = endLon;
            this.congestionFactor = congestionFactor;
            this.roadType = roadType;
        }

        public double getStartLat() { return startLat; }
        public double getStartLon() { return startLon; }
        public double getEndLat() { return endLat; }
        public double getEndLon() { return endLon; }
        public double getCongestionFactor() { return congestionFactor; }
        public String getRoadType() { return roadType; }
    }

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

    public double getFreeFlowTravelTimeMinutes() { return freeFlowTravelTimeMinutes; }
    public void setFreeFlowTravelTimeMinutes(double freeFlowTravelTimeMinutes) { this.freeFlowTravelTimeMinutes = freeFlowTravelTimeMinutes; }

    public double getCongestionDelayMinutes() { return congestionDelayMinutes; }
    public void setCongestionDelayMinutes(double congestionDelayMinutes) { this.congestionDelayMinutes = congestionDelayMinutes; }

    public String getLiveRouteStatus() { return liveRouteStatus; }
    public void setLiveRouteStatus(String liveRouteStatus) { this.liveRouteStatus = liveRouteStatus; }

    public String getLiveAdvisoryMessage() { return liveAdvisoryMessage; }
    public void setLiveAdvisoryMessage(String liveAdvisoryMessage) { this.liveAdvisoryMessage = liveAdvisoryMessage; }

    public int getNodesExplored() { return nodesExplored; }
    public void setNodesExplored(int nodesExplored) { this.nodesExplored = nodesExplored; }

    public double getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(double executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getAlgorithmUsed() { return algorithmUsed; }
    public void setAlgorithmUsed(String algorithmUsed) { this.algorithmUsed = algorithmUsed; }

    public List<double[]> getRawCoordinates() { return rawCoordinates; }
    public void setRawCoordinates(List<double[]> rawCoordinates) { this.rawCoordinates = rawCoordinates; }

    public List<SegmentDetail> getSegmentDetails() { return segmentDetails; }
    public void setSegmentDetails(List<SegmentDetail> segmentDetails) { this.segmentDetails = segmentDetails; }
}
