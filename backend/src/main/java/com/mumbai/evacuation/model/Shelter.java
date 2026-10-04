package com.mumbai.evacuation.model;

/**
 * Evacuation shelter with live occupancy (operator-maintained).
 * Simulations never touch these fields; they keep their own per-run occupancy.
 */
public class Shelter {
    private final long id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final long nearestNodeId;
    private final boolean floodProne;
    private int totalCapacity;
    private int currentOccupancy;

    public Shelter(long id, String name, double latitude, double longitude, long nearestNodeId,
                   int totalCapacity, boolean floodProne) {
        if (totalCapacity <= 0) throw new IllegalArgumentException("Shelter capacity must be positive: " + name);
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.nearestNodeId = nearestNodeId;
        this.totalCapacity = totalCapacity;
        this.floodProne = floodProne;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getNearestNodeId() { return nearestNodeId; }

    /** Low-lying site that must not be used as a shelter during floods. */
    public boolean isFloodProne() { return floodProne; }

    public synchronized int getTotalCapacity() { return totalCapacity; }
    public synchronized int getCurrentOccupancy() { return currentOccupancy; }
    public synchronized int getRemainingCapacity() { return Math.max(0, totalCapacity - currentOccupancy); }
    public synchronized boolean isFull() { return getRemainingCapacity() <= 0; }

    /** @throws IllegalArgumentException if not positive or below current occupancy */
    public synchronized void setTotalCapacity(int totalCapacity) {
        if (totalCapacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (totalCapacity < currentOccupancy) {
            throw new IllegalArgumentException("Capacity " + totalCapacity + " is below current occupancy " + currentOccupancy);
        }
        this.totalCapacity = totalCapacity;
    }

    /** @throws IllegalArgumentException if negative or above capacity */
    public synchronized void setCurrentOccupancy(int occupancy) {
        if (occupancy < 0 || occupancy > totalCapacity) {
            throw new IllegalArgumentException("Occupancy must be between 0 and " + totalCapacity);
        }
        this.currentOccupancy = occupancy;
    }

    public synchronized void resetOccupancy() { this.currentOccupancy = 0; }

    /** Admits up to {@code people} arrivals; returns how many fitted. */
    public synchronized int admit(int people) {
        int admitted = Math.max(0, Math.min(people, totalCapacity - currentOccupancy));
        currentOccupancy += admitted;
        return admitted;
    }

    /** Releases up to {@code people} occupants; returns how many left. */
    public synchronized int release(int people) {
        int released = Math.max(0, Math.min(people, currentOccupancy));
        currentOccupancy -= released;
        return released;
    }

    @Override
    public synchronized String toString() {
        return "Shelter{id=" + id + ", name='" + name + "', occupancy=" + currentOccupancy + "/" + totalCapacity + '}';
    }
}
