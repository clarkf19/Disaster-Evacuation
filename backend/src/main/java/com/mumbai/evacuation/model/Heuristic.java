package com.mumbai.evacuation.model;

/**
 * Admissible A* heuristic: straight-line distance divided by the fastest speed
 * limit present in the graph. A real path is never shorter than the straight
 * line, no edge is faster than the network maximum, and every cost overlay only
 * multiplies free-flow time by a factor >= 1 — so the heuristic never
 * overestimates and A* stays optimal.
 */
public final class Heuristic {
    private Heuristic() {}

    public static double haversineDistanceMeters(Node n1, Node n2) {
        if (n1 == null || n2 == null) return 0.0;
        return GeoUtils.haversineMeters(n1.getLatitude(), n1.getLongitude(), n2.getLatitude(), n2.getLongitude());
    }

    public static double travelTimeLowerBoundSeconds(Node current, Node target, double maxSpeedKmH) {
        return haversineDistanceMeters(current, target) / (maxSpeedKmH * 1000.0 / 3600.0);
    }
}
