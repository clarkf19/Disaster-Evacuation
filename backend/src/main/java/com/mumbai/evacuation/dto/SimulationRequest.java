package com.mumbai.evacuation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Evacuation simulation input. Either reference a preset with {@code scenarioId},
 * or supply custom {@code groups} (and optionally {@code disasters}; when omitted
 * the currently active live disasters are used). Any assumption left null uses
 * the server default (see {@code evacuation.*} in application.yml).
 *
 * Simulations are sandboxed: they never modify live disasters, shelter
 * occupancy or the road state that other users route against.
 */
public record SimulationRequest(
        @Size(max = 64) String scenarioId,
        @Size(max = 20) List<@Valid DisasterRequest> disasters,
        @Size(max = 50) List<@Valid Group> groups,
        @DecimalMin("1") @DecimalMax("60") Double personsPerVehicle,
        @DecimalMin("0.25") @DecimalMax("24") Double evacuationWindowHours,
        @DecimalMin("0.1") @DecimalMax("5") Double capacityScale,
        @DecimalMin("0") @DecimalMax("1") Double walkShare,
        @DecimalMin("0") @DecimalMax("1") Double transitShare) {

    public SimulationRequest(String scenarioId, List<DisasterRequest> disasters, List<Group> groups) {
        this(scenarioId, disasters, groups, null, null, null, null, null);
    }

    public record Group(
            @Size(max = 64) String id,
            @Size(max = 100) String name,
            @NotNull @DecimalMin(MumbaiBounds.MIN_LAT) @DecimalMax(MumbaiBounds.MAX_LAT) Double lat,
            @NotNull @DecimalMin(MumbaiBounds.MIN_LON) @DecimalMax(MumbaiBounds.MAX_LON) Double lon,
            @NotNull @Min(1) @Max(1_000_000) Integer count,
            @Size(max = 100) String wardName) {}

    /** Applies any overrides in this request on top of the server defaults. */
    public SimulationParams paramsOver(SimulationParams defaults) {
        double walk = walkShare != null ? walkShare : defaults.walkShare();
        double transit = transitShare != null ? transitShare : defaults.transitShare();
        if (walkShare != null && transitShare == null) transit = Math.min(transit, 1.0 - walk);
        if (transitShare != null && walkShare == null) walk = Math.min(walk, 1.0 - transit);
        return new SimulationParams(
                personsPerVehicle != null ? personsPerVehicle : defaults.personsPerVehicle(),
                evacuationWindowHours != null ? evacuationWindowHours : defaults.evacuationWindowHours(),
                capacityScale != null ? capacityScale : defaults.capacityScale(),
                walk, transit);
    }
}
