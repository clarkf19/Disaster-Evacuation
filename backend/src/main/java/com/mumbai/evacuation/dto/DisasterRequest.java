package com.mumbai.evacuation.dto;

public class DisasterRequest {
    private String id;
    private String type;  // FLOOD, FIRE, BRIDGE_COLLAPSE, CHEMICAL_LEAK
    private double latitude;
    private double longitude;
    private double radiusMeters;
    private boolean blockRoads;
    private double congestionMultiplier;
    private String description;

    public DisasterRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(double radiusMeters) { this.radiusMeters = radiusMeters; }

    public boolean isBlockRoads() { return blockRoads; }
    public void setBlockRoads(boolean blockRoads) { this.blockRoads = blockRoads; }

    public double getCongestionMultiplier() { return congestionMultiplier; }
    public void setCongestionMultiplier(double congestionMultiplier) { this.congestionMultiplier = congestionMultiplier; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
