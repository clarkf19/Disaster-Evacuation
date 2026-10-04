package com.mumbai.evacuation.dto;

import com.mumbai.evacuation.disaster.DisasterType;
import jakarta.validation.constraints.*;

/**
 * Request to inject a disaster. Coordinates must fall inside the wider Mumbai
 * region; radius is limited to 50 m – 10 km; the congestion multiplier (used
 * when roads are not fully blocked) to 1–10x.
 */
public record DisasterRequest(
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]*", message = "may only contain letters, digits, '-' and '_'") String id,
        @NotNull DisasterType type,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double latitude,
        @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double longitude,
        @NotNull @DecimalMin("50") @DecimalMax("10000") Double radiusMeters,
        boolean blockRoads,
        @DecimalMin("1.0") @DecimalMax("10.0") Double congestionMultiplier,
        @Size(max = 200) String description) {
}
