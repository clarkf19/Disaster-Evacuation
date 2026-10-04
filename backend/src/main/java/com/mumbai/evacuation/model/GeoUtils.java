package com.mumbai.evacuation.model;

/**
 * Small geodesy helpers. Distances in metres.
 *
 * The point-to-segment test projects onto a local equirectangular plane centred
 * on the query point; at city scale (< 50 km) the error is negligible.
 */
public final class GeoUtils {
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {}

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Shortest distance from point P to the segment AB. */
    public static double pointToSegmentMeters(double pLat, double pLon,
                                              double aLat, double aLon,
                                              double bLat, double bLon) {
        double mPerDegLat = Math.PI * EARTH_RADIUS_METERS / 180.0;
        double mPerDegLon = mPerDegLat * Math.cos(Math.toRadians(pLat));
        double ax = (aLon - pLon) * mPerDegLon, ay = (aLat - pLat) * mPerDegLat;
        double bx = (bLon - pLon) * mPerDegLon, by = (bLat - pLat) * mPerDegLat;
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lenSq));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.sqrt(cx * cx + cy * cy);
    }
}
