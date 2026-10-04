package com.mumbai.evacuation.dto;

import com.mumbai.evacuation.model.TravelMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Route planner query. {@code mode} defaults to DRIVE. */
public record LiveRouteRequest(
        @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double fromLat,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double fromLon,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double toLat,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double toLon,
        TravelMode mode) {

    public LiveRouteRequest(Double fromLat, Double fromLon, Double toLat, Double toLon) {
        this(fromLat, fromLon, toLat, toLon, TravelMode.DRIVE);
    }

    public TravelMode modeOrDefault() {
        return mode == null ? TravelMode.DRIVE : mode;
    }
}
