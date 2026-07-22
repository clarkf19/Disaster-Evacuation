package com.mumbai.evacuation.disaster;

import java.util.Objects;

/**
 * Model representing an active disaster event in Mumbai.
 * 
 * Design Decision Rationale:
 * - Disasters have a center coordinate (lat/lon) and an affected radius (meters).
 * - Severe events (FLOOD, FIRE, BRIDGE_COLLAPSE, CHEMICAL_LEAK) cause complete road blockages
 *   or significant congestion multipliers (e.g. 2.5x to 5.0x travel time penalty).
 */
public class DisasterEvent {
    private final String id;
    private final DisasterType type;
    private final double centerLatitude;
    private final double centerLongitude;
    private final double affectedRadiusMeters;
    private final boolean blockRoads;
    private final double congestionMultiplier;
    private final String description;

    public DisasterEvent(String id, DisasterType type, double centerLatitude, double centerLongitude, 
                         double affectedRadiusMeters, boolean blockRoads, double congestionMultiplier, String description) {
        this.id = id;
        this.type = type;
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.affectedRadiusMeters = affectedRadiusMeters;
        this.blockRoads = blockRoads;
        this.congestionMultiplier = Math.max(1.0, congestionMultiplier);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public DisasterType getType() {
        return type;
    }

    public double getCenterLatitude() {
        return centerLatitude;
    }

    public double getCenterLongitude() {
        return centerLongitude;
    }

    public double getAffectedRadiusMeters() {
        return affectedRadiusMeters;
    }

    public boolean isBlockRoads() {
        return blockRoads;
    }

    public double getCongestionMultiplier() {
        return congestionMultiplier;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisasterEvent that = (DisasterEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
