package com.mumbai.evacuation.model;

/**
 * Spatial Heuristics for A* Pathfinding.
 * 
 * Design & Admissibility Rationale:
 * - Uses the Haversine formula to compute great-circle distance in meters between two lat/lon nodes.
 * - Admissible Heuristic: To guarantee A* returns the true optimal shortest travel time, the heuristic h(n)
 *   must NEVER overestimate the actual travel time remaining from node n to target.
 * - Estimated Travel Time h(n) = HaversineDistance(n, target) / MaxNetworkSpeedMps
 * - Max network speed = 80 km/h (Motorway) = 22.22 m/s.
 * - Because any actual road path distance is >= straight-line distance, and any road speed is <= max speed,
 *   h(n) is mathematically guaranteed to be strictly admissible and consistent.
 */
public class Heuristic {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MAX_SPEED_MPS = (80.0 * 1000.0) / 3600.0; // 80 km/h in m/s

    public static double haversineDistanceMeters(Node n1, Node n2) {
        if (n1 == null || n2 == null) return 0.0;
        
        double lat1 = Math.toRadians(n1.getLatitude());
        double lon1 = Math.toRadians(n1.getLongitude());
        double lat2 = Math.toRadians(n2.getLatitude());
        double lon2 = Math.toRadians(n2.getLongitude());

        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;

        double a = Math.sin(dlat / 2.0) * Math.sin(dlat / 2.0) +
                   Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2.0) * Math.sin(dlon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    public static double calculateAdmissibleTravelTimeHeuristic(Node current, Node target) {
        double distMeters = haversineDistanceMeters(current, target);
        return distMeters / MAX_SPEED_MPS; // Estimated travel time in seconds
    }
}
