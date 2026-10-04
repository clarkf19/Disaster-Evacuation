package com.mumbai.evacuation.model;

/**
 * How evacuees move.
 *
 * DRIVE   — cars/buses on drivable roads, respecting one-way streets; creates road traffic.
 * WALK    — on foot at {@link #WALK_KMH} along any road in either direction; walkers are
 *           assumed not to congest roads.
 * TRANSIT — walk to a suburban station, ride the train, walk on; may also walk the whole way
 *           if that is faster. Trains get crowded (rail capacity in persons/hour).
 */
public enum TravelMode {
    DRIVE,
    WALK,
    TRANSIT;

    /** Average walking speed incl. crowding and crossings (km/h). */
    public static final double WALK_KMH = 4.5;
    /** Average suburban running speed between stations, excluding dwell (km/h). */
    public static final double RAIL_KMH = 40.0;
    /** Dwell time per station stop (s). */
    public static final double RAIL_DWELL_SECONDS = 30.0;
    /** Average wait for a train when entering a station (s) — trains every ~8-10 min in a disruption. */
    public static final double BOARDING_WAIT_SECONDS = 300.0;
    /** Time to leave a station after alighting (s). */
    public static final double ALIGHTING_SECONDS = 60.0;
    /** Persons/hour a line can carry in one direction during an emergency (12-car rakes, reduced frequency). */
    public static final int RAIL_CAPACITY_PER_HOUR = 30_000;

    /** Upper bound on speed in this mode, for an admissible A* heuristic (km/h). */
    public double maxSpeedKmH(Graph graph) {
        return switch (this) {
            case DRIVE -> graph.getMaxRoadSpeedKmH();
            case WALK -> WALK_KMH;
            case TRANSIT -> Math.max(RAIL_KMH, WALK_KMH);
        };
    }

    public boolean allows(Edge edge) {
        return switch (this) {
            case DRIVE -> edge.getKind() == Edge.Kind.ROAD;
            case WALK -> edge.isRoad();
            case TRANSIT -> true;
        };
    }
}
