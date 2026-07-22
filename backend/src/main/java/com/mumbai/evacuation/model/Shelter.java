package com.mumbai.evacuation.model;

/**
 * Model representing an Evacuation Shelter in Mumbai.
 * 
 * Design Decision Rationale:
 * - Shelters are designated safe locations (stadiums, grounds, schools, hospitals).
 * - Tracks total capacity and real-time occupancy.
 * - Nearest graph node ID enables fast pathfinding routing from evacuee source locations to shelter.
 */
public class Shelter {
    private final long id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private long nearestNodeId;
    private int totalCapacity;
    private int currentOccupancy;

    public Shelter(long id, String name, double latitude, double longitude, long nearestNodeId, int totalCapacity) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.nearestNodeId = nearestNodeId;
        this.totalCapacity = totalCapacity;
        this.currentOccupancy = 0;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getNearestNodeId() {
        return nearestNodeId;
    }

    public void setNearestNodeId(long nearestNodeId) {
        this.nearestNodeId = nearestNodeId;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public int getCurrentOccupancy() {
        return currentOccupancy;
    }

    public synchronized boolean reserveCapacity(int count) {
        if (getRemainingCapacity() >= count) {
            this.currentOccupancy += count;
            return true;
        }
        return false;
    }

    public synchronized void releaseOccupancy(int count) {
        this.currentOccupancy = Math.max(0, this.currentOccupancy - count);
    }

    public synchronized void resetOccupancy() {
        this.currentOccupancy = 0;
    }

    public int getRemainingCapacity() {
        return Math.max(0, totalCapacity - currentOccupancy);
    }

    public boolean isFull() {
        return getRemainingCapacity() <= 0;
    }

    @Override
    public String toString() {
        return "Shelter{" + "id=" + id + ", name='" + name + '\'' + ", capacity=" + currentOccupancy + "/" + totalCapacity + '}';
    }
}
