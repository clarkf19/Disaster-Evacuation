package com.mumbai.evacuation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LiveRouteRequest(
        @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double fromLat,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double fromLon,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double toLat,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double toLon) {
}
