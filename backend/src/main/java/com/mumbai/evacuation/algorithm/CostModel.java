package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.TravelMode;

import java.util.Map;

/**
 * Edge travel times per travel mode.
 *
 * <pre>
 *   DRIVE    ROAD: free-flow time × hazard multiplier × traffic congestion factor
 *   WALK     ROAD / ROAD_REVERSE: distance / walking speed × hazard multiplier
 *   TRANSIT  walking edges as WALK; TRANSFER: wait/exit time; RAIL: running + dwell
 *            × hazard multiplier × crowding factor
 * </pre>
 * Edges not usable in the mode, or blocked by a hazard, cost +infinity.
 * Every multiplier is >= 1, so costs never drop below the mode's speed bound and
 * A* stays admissible.
 */
public final class CostModel {

    static final double WALK_MPS = TravelMode.WALK_KMH * 1000.0 / 3600.0;

    private CostModel() {}

    public static EdgeCost of(TravelMode mode, HazardOverlay overlay) {
        return of(mode, overlay, null);
    }

    /**
     * @param loadPerHour optional simulated load per edge id: vehicles/hour on roads
     *                    (only drivers create it) and persons/hour on rail edges
     */
    public static EdgeCost of(TravelMode mode, HazardOverlay overlay, Map<Long, Double> loadPerHour) {
        return edge -> {
            if (!mode.allows(edge) || overlay.isBlocked(edge)) return Double.POSITIVE_INFINITY;
            double base = switch (edge.getKind()) {
                case ROAD -> mode == TravelMode.DRIVE ? edge.getFreeFlowSeconds() : edge.getDistanceMeters() / WALK_MPS;
                case ROAD_REVERSE -> edge.getDistanceMeters() / WALK_MPS;
                case RAIL, TRANSFER -> edge.getFreeFlowSeconds();
            };
            double factor = overlay.multiplier(edge);
            if (loadPerHour != null && congestible(mode, edge)) {
                factor *= edge.congestionFactorFor(loadPerHour.getOrDefault(edge.getId(), 0.0));
            }
            return base * factor;
        };
    }

    /** Edges whose travel time depends on simulated load: driven roads and trains. */
    public static boolean congestible(TravelMode mode, Edge edge) {
        return (mode == TravelMode.DRIVE && edge.getKind() == Edge.Kind.ROAD) || edge.getKind() == Edge.Kind.RAIL;
    }
}
