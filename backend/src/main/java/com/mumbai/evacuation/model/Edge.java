package com.mumbai.evacuation.model;

/**
 * Immutable directional road segment connecting two nodes.
 *
 * Edges only carry static road attributes. Everything that changes at runtime
 * (disaster blockages, congestion multipliers, simulated traffic load) lives in
 * separate overlays (see {@link com.mumbai.evacuation.disaster.HazardOverlay} and
 * {@link com.mumbai.evacuation.algorithm.EdgeCost}) so concurrent route queries
 * never observe half-updated state and simulations never leak into live routing.
 *
 * Congestion mapping (traffic in vehicles/hour vs. capacity in vehicles/hour):
 *   ratio 0.00-0.30 -> 1.0, 0.30-0.60 -> 1.3, 0.60-0.80 -> 1.7, > 0.80 -> 2.5
 */
public final class Edge {
    private final long id;
    private final long sourceNodeId;
    private final long targetNodeId;
    private final double distanceMeters;
    private final String roadType;
    private final double speedLimitKmH;
    private final int capacityVehiclesPerHour;
    private final double freeFlowSeconds;

    public Edge(long id, long sourceNodeId, long targetNodeId, double distanceMeters,
                String roadType, double speedLimitKmH, int capacityVehiclesPerHour) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.distanceMeters = Math.max(1.0, distanceMeters);
        this.roadType = roadType;
        this.speedLimitKmH = Math.max(10.0, speedLimitKmH);
        this.capacityVehiclesPerHour = Math.max(1, capacityVehiclesPerHour);
        this.freeFlowSeconds = this.distanceMeters / (this.speedLimitKmH * 1000.0 / 3600.0);
    }

    public long getId() { return id; }
    public long getSourceNodeId() { return sourceNodeId; }
    public long getTargetNodeId() { return targetNodeId; }
    public double getDistanceMeters() { return distanceMeters; }
    public String getRoadType() { return roadType; }
    public double getSpeedLimitKmH() { return speedLimitKmH; }

    /** Road capacity in vehicles per hour. */
    public int getCapacity() { return capacityVehiclesPerHour; }

    /** Travel time in seconds at the speed limit with no congestion. Always > 0. */
    public double getFreeFlowSeconds() { return freeFlowSeconds; }

    /** Congestion factor for a given traffic load (vehicles/hour) on this edge. */
    public double congestionFactorFor(double vehiclesPerHour) {
        double ratio = vehiclesPerHour / capacityVehiclesPerHour;
        if (ratio <= 0.30) return 1.0;
        if (ratio <= 0.60) return 1.3;
        if (ratio <= 0.80) return 1.7;
        return 2.5;
    }

    @Override
    public String toString() {
        return "Edge{id=" + id + ", src=" + sourceNodeId + ", dst=" + targetNodeId +
               ", dist=" + distanceMeters + "m, speed=" + speedLimitKmH + "km/h}";
    }
}
