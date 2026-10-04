package com.mumbai.evacuation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Evacuation simulation input. Either reference a preset with {@code scenarioId},
 * or supply custom {@code groups} (and optionally {@code disasters}; when omitted
 * the currently active live disasters are used).
 *
 * Simulations are sandboxed: they never modify live disasters, shelter
 * occupancy or the road state that other users route against.
 */
public record SimulationRequest(
        @Size(max = 64) String scenarioId,
        @Size(max = 20) List<@Valid DisasterRequest> disasters,
        @Size(max = 50) List<@Valid Group> groups) {

    public record Group(
            @Size(max = 64) String id,
            @Size(max = 100) String name,
            @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double lat,
            @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double lon,
            @NotNull @Min(1) @Max(1_000_000) Integer count,
            @Size(max = 100) String wardName) {}
}
