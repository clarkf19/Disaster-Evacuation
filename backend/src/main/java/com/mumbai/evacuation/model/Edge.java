package com.mumbai.evacuation.model;

/**
 * Represents a directional road segment connecting two nodes in Mumbai.
 * 
 * Design Decision & Formula Rationale:
 * 1. Dynamic Congestion Mapping:
 *    - Congestion ratio = currentTraffic / capacity
 *    - Ratio 0.00 - 0.30 => Congestion Factor = 1.0
 *    - Ratio 0.30 - 0.60 => Congestion Factor = 1.3
 *    - Ratio 0.60 - 0.80 => Congestion Factor = 1.7
 *    - Ratio > 0.80      => Congestion Factor = 2.5
 * 2. Edge Travel Time (Cost Weight in seconds):
 *    - Speed in meters/second = (speedLimitKmH * 1000.0) / 3600.0
 *    - Free-flow Travel Time = distanceMeters / speedMetersPerSecond
 *    - Dynamic Effective Travel Time = Free-flow Travel Time * Congestion Factor
 *    - If blocked == true, cost is Double.POSITIVE_INFINITY (impassable).
 *    - Travel time is guaranteed to stay strictly positive (> 0.0) preventing zero/negative cycle bugs in Dijkstra.
 */
public class Edge {
    private final long id;
    private final long sourceNodeId;
    private final long targetNodeId;
    private final double distanceMeters;
    private final String roadType;
    private final double speedLimitKmH;
    private final int capacity;
    
    private int currentTraffic;
    private boolean blocked;
    private double customCongestionMultiplier = 1.0;

    public Edge(long id, long sourceNodeId, long targetNodeId, double distanceMeters, 
                String roadType, double speedLimitKmH, int capacity) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.distanceMeters = Math.max(1.0, distanceMeters);
        this.roadType = roadType;
        this.speedLimitKmH = Math.max(10.0, speedLimitKmH);
        this.capacity = Math.max(1, capacity);
        this.currentTraffic = 0;
        this.blocked = false;
    }

    public long getId() {
        return id;
    }

    public long getSourceNodeId() {
        return sourceNodeId;
    }

    public long getTargetNodeId() {
        return targetNodeId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getRoadType() {
        return roadType;
    }

    public double getSpeedLimitKmH() {
        return speedLimitKmH;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentTraffic() {
        return currentTraffic;
    }

    public void setCurrentTraffic(int currentTraffic) {
        this.currentTraffic = Math.max(0, currentTraffic);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public double getCustomCongestionMultiplier() {
        return customCongestionMultiplier;
    }

    public void setCustomCongestionMultiplier(double customCongestionMultiplier) {
        this.customCongestionMultiplier = Math.max(1.0, customCongestionMultiplier);
    }

    /**
     * Calculates dynamic congestion factor according to system spec.
     */
    public double getCongestionFactor() {
        double ratio = (double) currentTraffic / (double) capacity;
        double factor;
        if (ratio <= 0.30) {
            factor = 1.0;
        } else if (ratio <= 0.60) {
            factor = 1.3;
        } else if (ratio <= 0.80) {
            factor = 1.7;
        } else {
            factor = 2.5;
        }
        return factor * customCongestionMultiplier;
    }

    /**
     * Calculates travel time in seconds. Returns Double.POSITIVE_INFINITY if road is blocked.
     */
    public double getTravelTimeSeconds() {
        if (blocked) {
            return Double.POSITIVE_INFINITY;
        }
        double speedMps = (speedLimitKmH * 1000.0) / 3600.0;
        double freeFlowTimeSeconds = distanceMeters / speedMps;
        return freeFlowTimeSeconds * getCongestionFactor();
    }

    @Override
    public String toString() {
        return "Edge{" + "id=" + id + ", src=" + sourceNodeId + ", dst=" + targetNodeId + 
               ", dist=" + distanceMeters + "m, speed=" + speedLimitKmH + "km/h, blocked=" + blocked + '}';
    }
}
