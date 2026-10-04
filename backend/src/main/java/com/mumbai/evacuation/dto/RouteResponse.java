package com.mumbai.evacuation.dto;

import java.util.ArrayList;
import java.util.List;

/** Route computed on the in-memory multimodal network with the active hazard overlay applied. */
public class RouteResponse {
    private boolean pathFound;
    private String travelMode = "DRIVE";
    private long sourceNodeId;
    private long targetNodeId;
    private double sourceSnapMeters;
    private double targetSnapMeters;
    private double totalDistanceKm;
    private double totalTravelTimeMinutes;
    private double freeFlowTravelTimeMinutes;
    private double congestionDelayMinutes;
    private Double lowestElevationM;
    private String liveRouteStatus; // CLEAR | MODERATE_TRAFFIC | HEAVY_CONGESTION | DISASTER_BYPASS | HAZARD_EGRESS | UNPASSABLE | OUT_OF_COVERAGE
    private String liveAdvisoryMessage;
    private int nodesExplored;
    private double executionTimeMs;
    private String algorithmUsed;
    private List<double[]> rawCoordinates = new ArrayList<>(); // [lat, lon] pairs
    private List<SegmentDetail> segmentDetails = new ArrayList<>();
    private List<Leg> legs = new ArrayList<>();

    /** One edge of the route; {@code kind} is drive, walk or rail. */
    public record SegmentDetail(double startLat, double startLon, double endLat, double endLon,
                                double congestionFactor, String roadType, String kind) {}

    /**
     * One step of the itinerary.
     * type: DRIVE | WALK | WAIT (for a train) | TRAIN; line/fromName/toName are set for TRAIN legs.
     */
    public record Leg(String type, String line, String fromName, String toName, double minutes, double distanceKm, int stops) {}

    public boolean isPathFound() { return pathFound; }
    public void setPathFound(boolean pathFound) { this.pathFound = pathFound; }
    public String getTravelMode() { return travelMode; }
    public void setTravelMode(String travelMode) { this.travelMode = travelMode; }
    public long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(long sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public long getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(long targetNodeId) { this.targetNodeId = targetNodeId; }
    public double getSourceSnapMeters() { return sourceSnapMeters; }
    public void setSourceSnapMeters(double sourceSnapMeters) { this.sourceSnapMeters = sourceSnapMeters; }
    public double getTargetSnapMeters() { return targetSnapMeters; }
    public void setTargetSnapMeters(double targetSnapMeters) { this.targetSnapMeters = targetSnapMeters; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }
    public double getTotalTravelTimeMinutes() { return totalTravelTimeMinutes; }
    public void setTotalTravelTimeMinutes(double totalTravelTimeMinutes) { this.totalTravelTimeMinutes = totalTravelTimeMinutes; }
    public double getFreeFlowTravelTimeMinutes() { return freeFlowTravelTimeMinutes; }
    public void setFreeFlowTravelTimeMinutes(double freeFlowTravelTimeMinutes) { this.freeFlowTravelTimeMinutes = freeFlowTravelTimeMinutes; }
    public double getCongestionDelayMinutes() { return congestionDelayMinutes; }
    public void setCongestionDelayMinutes(double congestionDelayMinutes) { this.congestionDelayMinutes = congestionDelayMinutes; }
    public Double getLowestElevationM() { return lowestElevationM; }
    public void setLowestElevationM(Double lowestElevationM) { this.lowestElevationM = lowestElevationM; }
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
    public List<Leg> getLegs() { return legs; }
    public void setLegs(List<Leg> legs) { this.legs = legs; }
}
