package com.mumbai.evacuation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Route returned to the route planner.
 *
 * {@code routeSource} tells the client where the geometry came from:
 * TOMTOM_LIVE (TomTom with live traffic, hazard zones passed as avoid-areas and
 * verified server-side) or ROAD_GRAPH (our own hazard-aware A* on the OSM graph).
 */
public class LiveRouteResponse {
    private boolean pathFound;
    private String routeSource;
    private double distanceKm;
    private int liveTravelTimeMinutes;
    private int freeFlowTravelTimeMinutes;
    private int delayMinutes;
    private String liveStatus;        // CLEAR | SLOW_TRAFFIC | MODERATE_TRAFFIC | HEAVY_CONGESTION | DISASTER_BYPASS | HAZARD_EGRESS | UNPASSABLE
    private String advisoryMessage;
    private List<String> warnings = new ArrayList<>();
    private List<double[]> routeCoordinates = new ArrayList<>();  // [[lat, lon], ...]
    private List<SegmentInfo> segments = new ArrayList<>();

    public static class SegmentInfo {
        private final List<double[]> points;
        private final double congestionFactor; // 1.0=clear, 1.3=slow, 1.7=moderate, >=2.5=heavy

        public SegmentInfo(List<double[]> points, double congestionFactor) {
            this.points = points;
            this.congestionFactor = congestionFactor;
        }

        public List<double[]> getPoints() { return points; }
        public double getCongestionFactor() { return congestionFactor; }
    }

    public boolean isPathFound() { return pathFound; }
    public void setPathFound(boolean pathFound) { this.pathFound = pathFound; }
    public String getRouteSource() { return routeSource; }
    public void setRouteSource(String routeSource) { this.routeSource = routeSource; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public int getLiveTravelTimeMinutes() { return liveTravelTimeMinutes; }
    public void setLiveTravelTimeMinutes(int liveTravelTimeMinutes) { this.liveTravelTimeMinutes = liveTravelTimeMinutes; }
    public int getFreeFlowTravelTimeMinutes() { return freeFlowTravelTimeMinutes; }
    public void setFreeFlowTravelTimeMinutes(int freeFlowTravelTimeMinutes) { this.freeFlowTravelTimeMinutes = freeFlowTravelTimeMinutes; }
    public int getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(int delayMinutes) { this.delayMinutes = delayMinutes; }
    public String getLiveStatus() { return liveStatus; }
    public void setLiveStatus(String liveStatus) { this.liveStatus = liveStatus; }
    public String getAdvisoryMessage() { return advisoryMessage; }
    public void setAdvisoryMessage(String advisoryMessage) { this.advisoryMessage = advisoryMessage; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<double[]> getRouteCoordinates() { return routeCoordinates; }
    public void setRouteCoordinates(List<double[]> routeCoordinates) { this.routeCoordinates = routeCoordinates; }
    public List<SegmentInfo> getSegments() { return segments; }
    public void setSegments(List<SegmentInfo> segments) { this.segments = segments; }
}
