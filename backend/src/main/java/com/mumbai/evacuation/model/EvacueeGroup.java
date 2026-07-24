package com.mumbai.evacuation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing an Evacuee Group in the Mumbai Metropolitan Region.
 * 
 * Design Decision & System Model:
 * 1. Evacuee Granularity: Evacuees are modeled as groups (e.g. "5,000 evacuees from Ward F-North / Sion"),
 *    not single individuals. This ensures high system scalability and realistic macro-level emergency management.
 * 2. Real-Time Tracking: Tracks source road node ID, total count, assigned shelter ID, travel time, 
 *    distance, and route coordinate nodes.
 */
public class EvacueeGroup {

    public enum Status {
        PENDING,
        EVACUATING,
        EVACUATED,
        OVERFLOW
    }

    private final String id;
    private final String name;
    private final long sourceNodeId;
    private final int count;
    private final String wardName;
    
    private Status status;
    private Long assignedShelterId;
    private String assignedShelterName;
    private double travelTimeMinutes;
    private double travelDistanceKm;
    private List<Long> routeNodeIds = new ArrayList<>();
    private List<double[]> routeCoordinates = new ArrayList<>();

    public EvacueeGroup(String id, String name, long sourceNodeId, int count, String wardName) {
        this.id = id;
        this.name = name;
        this.sourceNodeId = sourceNodeId;
        this.count = Math.max(1, count);
        this.wardName = wardName;
        this.status = Status.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSourceNodeId() {
        return sourceNodeId;
    }

    public int getCount() {
        return count;
    }

    public String getWardName() {
        return wardName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getAssignedShelterId() {
        return assignedShelterId;
    }

    public void setAssignedShelterId(Long assignedShelterId) {
        this.assignedShelterId = assignedShelterId;
    }

    public String getAssignedShelterName() {
        return assignedShelterName;
    }

    public void setAssignedShelterName(String assignedShelterName) {
        this.assignedShelterName = assignedShelterName;
    }

    public double getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(double travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public double getTravelDistanceKm() {
        return travelDistanceKm;
    }

    public void setTravelDistanceKm(double travelDistanceKm) {
        this.travelDistanceKm = travelDistanceKm;
    }

    public List<Long> getRouteNodeIds() {
        return routeNodeIds;
    }

    public void setRouteNodeIds(List<Long> routeNodeIds) {
        this.routeNodeIds = routeNodeIds;
    }

    public List<double[]> getRouteCoordinates() {
        return routeCoordinates;
    }

    public void setRouteCoordinates(List<double[]> routeCoordinates) {
        this.routeCoordinates = routeCoordinates;
    }

    public void reset() {
        this.status = Status.PENDING;
        this.assignedShelterId = null;
        this.assignedShelterName = null;
        this.travelTimeMinutes = 0.0;
        this.travelDistanceKm = 0.0;
        this.routeNodeIds.clear();
        this.routeCoordinates.clear();
    }
}
