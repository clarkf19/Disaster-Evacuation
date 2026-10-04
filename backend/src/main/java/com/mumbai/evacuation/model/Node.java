package com.mumbai.evacuation.model;

/**
 * A graph vertex: a road intersection (OSM node id) or a suburban railway station.
 * Elevation is in metres (Copernicus GLO-30 surface model) or NaN when unknown.
 */
public final class Node {
    private final long id;
    private final double latitude;
    private final double longitude;
    private final double elevationM;
    private final String name;
    private final boolean station;

    public Node(long id, double latitude, double longitude) {
        this(id, latitude, longitude, Double.NaN, null, false);
    }

    public Node(long id, double latitude, double longitude, double elevationM, String name, boolean station) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationM = elevationM;
        this.name = name;
        this.station = station;
    }

    public long getId() { return id; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getElevationM() { return elevationM; }
    public boolean hasElevation() { return !Double.isNaN(elevationM); }
    /** Station name, or null for road intersections. */
    public String getName() { return name; }
    public boolean isStation() { return station; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Node other && id == other.id);
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    @Override
    public String toString() {
        return "Node{id=" + id + ", lat=" + latitude + ", lon=" + longitude + (station ? ", station=" + name : "") + '}';
    }
}
