package com.mumbai.evacuation.model;

/**
 * Immutable directed edge of the multimodal network.
 *
 * Kinds:
 * <ul>
 *   <li>ROAD — drivable OSM road segment (also walkable).</li>
 *   <li>ROAD_REVERSE — the opposite direction of a one-way road; walkable only,
 *       because one-way rules don't apply to pedestrians.</li>
 *   <li>RAIL — suburban train between consecutive stations (capacity in persons/hour).</li>
 *   <li>TRANSFER — walking between a station and its nearest road node; boarding
 *       includes the average wait for a train.</li>
 * </ul>
 *
 * Everything that changes at runtime (hazards, simulated load) lives in cost
 * overlays, never on the edge itself.
 *
 * Congestion mapping (load vs. capacity, both per hour):
 *   ratio 0.00-0.30 -> 1.0, 0.30-0.60 -> 1.3, 0.60-0.80 -> 1.7, > 0.80 -> 2.5
 */
public final class Edge {

    public enum Kind { ROAD, ROAD_REVERSE, RAIL, TRANSFER }

    private final long id;
    private final long sourceNodeId;
    private final long targetNodeId;
    private final double distanceMeters;
    private final String roadType;
    private final double speedLimitKmH;
    private final int capacityPerHour;
    private final double freeFlowSeconds;
    private final Kind kind;
    private final String line;

    /** Drivable road segment. */
    public Edge(long id, long sourceNodeId, long targetNodeId, double distanceMeters,
                String roadType, double speedLimitKmH, int capacityVehiclesPerHour) {
        this(id, sourceNodeId, targetNodeId, distanceMeters, roadType, speedLimitKmH, capacityVehiclesPerHour,
                Kind.ROAD, null, Double.NaN);
    }

    private Edge(long id, long sourceNodeId, long targetNodeId, double distanceMeters, String roadType,
                 double speedLimitKmH, int capacityPerHour, Kind kind, String line, double fixedSeconds) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.distanceMeters = Math.max(1.0, distanceMeters);
        this.roadType = roadType;
        this.speedLimitKmH = Math.max(10.0, speedLimitKmH);
        this.capacityPerHour = Math.max(1, capacityPerHour);
        this.kind = kind;
        this.line = line;
        this.freeFlowSeconds = Double.isNaN(fixedSeconds)
                ? this.distanceMeters / (this.speedLimitKmH * 1000.0 / 3600.0)
                : fixedSeconds;
    }

    /** Walk-only reverse of a one-way road. */
    public static Edge reverseForWalking(long id, Edge road) {
        return new Edge(id, road.targetNodeId, road.sourceNodeId, road.distanceMeters, road.roadType,
                road.speedLimitKmH, road.capacityPerHour, Kind.ROAD_REVERSE, null, Double.NaN);
    }

    /** Train link between consecutive stations: running time at {@code speedKmH} plus a station dwell. */
    public static Edge rail(long id, long fromStation, long toStation, double meters, String line,
                            double speedKmH, double dwellSeconds, int capacityPersonsPerHour) {
        double seconds = meters / (speedKmH * 1000.0 / 3600.0) + dwellSeconds;
        return new Edge(id, fromStation, toStation, meters, "rail", speedKmH, capacityPersonsPerHour,
                Kind.RAIL, line, seconds);
    }

    /** Station access/egress walk with a fixed time (e.g. including the wait for a train). */
    public static Edge transfer(long id, long from, long to, double meters, double seconds) {
        return new Edge(id, from, to, meters, "transfer", 10, Integer.MAX_VALUE, Kind.TRANSFER, null, seconds);
    }

    public long getId() { return id; }
    public long getSourceNodeId() { return sourceNodeId; }
    public long getTargetNodeId() { return targetNodeId; }
    public double getDistanceMeters() { return distanceMeters; }
    public String getRoadType() { return roadType; }
    public double getSpeedLimitKmH() { return speedLimitKmH; }
    public Kind getKind() { return kind; }
    public boolean isRoad() { return kind == Kind.ROAD || kind == Kind.ROAD_REVERSE; }
    /** Suburban line name for RAIL edges, otherwise null. */
    public String getLine() { return line; }

    /** Capacity per hour: vehicles for roads, persons for rail. */
    public int getCapacity() { return capacityPerHour; }

    /** Uncongested traversal time in seconds for this edge's own mode. Always > 0. */
    public double getFreeFlowSeconds() { return freeFlowSeconds; }

    /** Congestion factor for a given load per hour (same units as the capacity). */
    public double congestionFactorFor(double loadPerHour) {
        double ratio = loadPerHour / capacityPerHour;
        if (ratio <= 0.30) return 1.0;
        if (ratio <= 0.60) return 1.3;
        if (ratio <= 0.80) return 1.7;
        return 2.5;
    }

    @Override
    public String toString() {
        return "Edge{id=" + id + ", " + kind + ", src=" + sourceNodeId + ", dst=" + targetNodeId +
               ", dist=" + distanceMeters + "m" + (line != null ? ", line=" + line : "") + '}';
    }
}
