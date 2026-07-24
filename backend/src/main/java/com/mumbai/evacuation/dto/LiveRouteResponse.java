package com.mumbai.evacuation.dto;

import java.util.List;

/**
 * Response DTO for the live route proxy endpoint.
 * Contains all real-time traffic data computed server-side — no API key in response.
 */
public class LiveRouteResponse {

    private boolean pathFound;
    private double distanceKm;
    private int liveTravelTimeMinutes;
    private int freeFlowTravelTimeMinutes;
    private int delayMinutes;
    private String liveStatus;        // CLEAR | SLOW_TRAFFIC | MODERATE_TRAFFIC | HEAVY_CONGESTION
    private String advisoryMessage;
    private List<double[]> routeCoordinates;  // [[lat, lon], ...]
    private List<SegmentInfo> segments;

    public static class SegmentInfo {
        private double startLat;
        private double startLon;
        private double endLat;
        private double endLon;
        private double congestionFactor; // 1.0=clear, 1.3=slow, 1.7=moderate, 2.5=heavy

        public SegmentInfo() {}

        public double getStartLat() { return startLat; }
        public void setStartLat(double startLat) { this.startLat = startLat; }

        public double getStartLon() { return startLon; }
        public void setStartLon(double startLon) { this.startLon = startLon; }

        public double getEndLat() { return endLat; }
        public void setEndLat(double endLat) { this.endLat = endLat; }

        public double getEndLon() { return endLon; }
        public void setEndLon(double endLon) { this.endLon = endLon; }

        public double getCongestionFactor() { return congestionFactor; }
        public void setCongestionFactor(double congestionFactor) { this.congestionFactor = congestionFactor; }
    }

    public LiveRouteResponse() {}

    public boolean isPathFound() { return pathFound; }
    public void setPathFound(boolean pathFound) { this.pathFound = pathFound; }

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

    public List<double[]> getRouteCoordinates() { return routeCoordinates; }
    public void setRouteCoordinates(List<double[]> routeCoordinates) { this.routeCoordinates = routeCoordinates; }

    public List<SegmentInfo> getSegments() { return segments; }
    public void setSegments(List<SegmentInfo> segments) { this.segments = segments; }
}
