package com.mumbai.evacuation.dto;

import com.mumbai.evacuation.model.EvacuationStrategy;

import java.util.List;
import java.util.Map;

/**
 * Results of evacuation simulations. All records are plain data so they
 * serialise directly to JSON for the Command Centre UI.
 */
public final class EvacuationBenchmarkResult {

    private EvacuationBenchmarkResult() {}

    public record Comparison(ScenarioSummary scenario, StrategyMetrics naive, StrategyMetrics capacityAware) {}

    public record ScenarioSummary(String id, String name, String description,
                                  List<DisasterView> disasters, List<GroupInput> groups,
                                  List<Long> unsafeShelterIds, SimulationParams params) {}

    public record DisasterView(String id, String type, double lat, double lon, double radiusMeters,
                               boolean blockRoads, double congestionMultiplier, String description) {}

    public record GroupInput(String id, String name, double lat, double lon, int count, String wardName,
                             boolean insideHazardZone) {}

    public record StrategyMetrics(
            EvacuationStrategy strategy,
            int totalEvacuees,
            int evacueesHoused,
            int overflowEvacuees,
            int unreachableEvacuees,
            double housedPercent,
            double avgEvacuationTimeMinutes,   // person-weighted, housed evacuees only
            double maxEvacuationTimeMinutes,
            double avgTravelDistanceKm,        // person-weighted, housed evacuees only
            double shelterUtilizationPercent,  // housed / capacity of usable shelters
            int sheltersOverCapacity,          // shelters that had to turn people away
            double congestedRoadKm,            // km of road at congestion factor >= 1.7
            double crowdedRailKm,              // km of rail at crowding factor >= 1.7
            int reroutedAllocations,
            long executionTimeMs,
            Map<String, ModeStats> byMode,     // DRIVE / WALK / TRANSIT
            List<ShelterLoad> shelters,
            List<Allocation> allocations,
            List<GroupOutcome> groups) {}

    public record ModeStats(int persons, int housed, double avgMinutes) {}

    public record ShelterLoad(long id, String name, int capacity, int arrivals, int housed, boolean unsafe) {}

    public record Allocation(String groupId, String groupName, String mode, long shelterId, String shelterName,
                             int persons, int housed, double travelTimeMinutes, double distanceKm,
                             boolean rerouted, List<double[]> route) {}

    public record GroupOutcome(String id, String name, int count, int housed, int overflow, String status) {}
}
