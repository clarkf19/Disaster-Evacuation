package com.mumbai.evacuation.model;

import java.util.Objects;

/**
 * Represents a geographical road intersection/node in the Mumbai network.
 * 
 * Design Decision Rationale:
 * - Immutable latitude and longitude coordinates in EPSG:4326 (WGS 84).
 * - Primary key ID maps 1:1 with OpenStreetMap node IDs or landmark IDs.
 */
public class Node {
    private final long id;
    private final double latitude;
    private final double longitude;
    private String name;

    public Node(long id, double latitude, double longitude) {
        this(id, latitude, longitude, null);
    }

    public Node(long id, double latitude, double longitude, String name) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name != null ? name : "Node-" + id;
    }

    public long getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id == node.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node{" + "id=" + id + ", lat=" + latitude + ", lon=" + longitude + ", name='" + name + '\'' + '}';
    }
}
