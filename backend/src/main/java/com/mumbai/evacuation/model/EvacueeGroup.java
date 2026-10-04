package com.mumbai.evacuation.model;

/**
 * A cluster of people that must be evacuated from one location.
 * Immutable simulation input — results are reported separately.
 */
public record EvacueeGroup(String id, String name, double latitude, double longitude, int count, String wardName) {
    public EvacueeGroup {
        if (count <= 0) throw new IllegalArgumentException("Evacuee group '" + id + "' must have a positive count");
    }
}
