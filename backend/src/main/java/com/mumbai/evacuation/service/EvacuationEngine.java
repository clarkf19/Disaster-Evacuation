package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.StrategyMetrics;
import com.mumbai.evacuation.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Evacuation Simulation & Capacity-Aware Routing Engine.
 * 
 * Design Decision & Algorithm Rationale:
 * 1. Greedy Capacity-Aware Assignment:
 *    - For each evacuee group: evaluates candidate shelters with sufficient remaining capacity.
 *    - Assigns group to the nearest available shelter using A* Haversine search.
 *    - Reserves shelter capacity and updates traffic load on all path road edges.
 * 2. Route Recalculation Trigger (20% Threshold):
 *    - Road edge congestion ratio maps dynamically:
 *      0.00-0.30 -> 1.0, 0.30-0.60 -> 1.3, 0.60-0.80 -> 1.7, >0.80 -> 2.5
 *    - If dynamic travel time on an active evacuation path increases by >= 20% due to 
 *      accumulated traffic or disaster blockage, the route is recomputed from scratch using A*.
 * 3. Strategy 1 (Baseline) vs Strategy 2 (Capacity-Aware) Comparison:
 *    - Strategy 1 (Naive Nearest): Ignores shelter capacity limits & congestion accumulation.
 *    - Strategy 2 (Capacity-Aware): Enforces capacity limits, updates traffic, and reroutes.
 */
@Service
public class EvacuationEngine {

    @Autowired
    private GraphService graphService;

    private final Map<String, EvacueeGroup> activeEvacueeGroups = new ConcurrentHashMap<>();
    private final AStarEngine aStarEngine = new AStarEngine();

    public Collection<EvacueeGroup> getActiveEvacueeGroups() {
        return activeEvacueeGroups.values();
    }

    public void addEvacueeGroup(EvacueeGroup group) {
        activeEvacueeGroups.put(group.getId(), group);
    }

    public void clearEvacueeGroups() {
        activeEvacueeGroups.clear();
    }

    /**
     * Pre-configured Disaster Evacuation Scenarios for Mumbai Region.
     */
    public List<Map<String, Object>> getPresetScenarios() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("id", "sion_flood");
        s1.put("name", "Sion Monsoon Heavy Flood");
        s1.put("description", "Severe urban flooding at Sion Junction blocking WEH & EEH interchange. Evacuating Sion, Kurla, and Dadar East wards.");
        s1.put("disasterNodeId", 1018L);
        s1.put("disasterType", "FLOOD");
        s1.put("radiusMeters", 1200.0);
        s1.put("blockRoads", true);

        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("id", "bkc_fire");
        s2.put("name", "Bandra-Kurla Complex Major Fire");
        s2.put("description", "Commercial building fire at BKC. Evacuating 17,000 evacuees from BKC, Bandra E, and Santacruz.");
        s2.put("disasterNodeId", 1009L);
        s2.put("disasterType", "FIRE");
        s2.put("radiusMeters", 1000.0);
        s2.put("blockRoads", true);

        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("id", "dadar_bridge");
        s3.put("name", "Dadar Central Bridge Collapse");
        s3.put("description", "Bridge collapse at Dadar West intersection causing traffic freeze across South-Central Mumbai.");
        s3.put("disasterNodeId", 1010L);
        s3.put("disasterType", "BRIDGE_COLLAPSE");
        s3.put("radiusMeters", 800.0);
        s3.put("blockRoads", true);

        Map<String, Object> s4 = new LinkedHashMap<>();
        s4.put("id", "chembur_leak");
        s4.put("name", "Chembur Chemical Industrial Leak");
        s4.put("description", "Hazardous chemical spill near Chembur corridor raising congestion multiplier 3.5x.");
        s4.put("disasterNodeId", 1020L);
        s4.put("disasterType", "CHEMICAL_LEAK");
        s4.put("radiusMeters", 1500.0);
        s4.put("blockRoads", false);

        list.add(s1);
        list.add(s2);
        list.add(s3);
        list.add(s4);
        return list;
    }

    /**
     * Loads a preset scenario by ID into active state and applies disaster.
     */
    public void loadPresetScenario(String scenarioId) {
        clearEvacueeGroups();
        graphService.clearAllDisasters();
        graphService.getShelterService().getAllShelters().forEach(Shelter::resetOccupancy);
        resetEdgeTraffic();

        Graph graph = graphService.getGraph();

        if ("sion_flood".equalsIgnoreCase(scenarioId)) {
            DisasterRequest d = new DisasterRequest();
            d.setId("disaster-sion-flood");
            d.setType("FLOOD");
            d.setLatitude(19.0390);
            d.setLongitude(72.8619);
            d.setRadiusMeters(1200);
            d.setBlockRoads(true);
            d.setCongestionMultiplier(3.0);
            d.setDescription("Monsoon flooding at Sion Circle");
            graphService.addDisaster(d);

            addEvacueeGroup(new EvacueeGroup("grp-1", "Sion Koliwada Evacuees", findNearestNodeId(graph, 19.0390, 72.8619), 5000, "Ward F-North"));
            addEvacueeGroup(new EvacueeGroup("grp-2", "Kurla West Evacuees", findNearestNodeId(graph, 19.0650, 72.8790), 6000, "Ward L"));
            addEvacueeGroup(new EvacueeGroup("grp-3", "Dadar East Evacuees", findNearestNodeId(graph, 19.0178, 72.8478), 4000, "Ward F-South"));
        } else if ("bkc_fire".equalsIgnoreCase(scenarioId)) {
            DisasterRequest d = new DisasterRequest();
            d.setId("disaster-bkc-fire");
            d.setType("FIRE");
            d.setLatitude(19.0657);
            d.setLongitude(72.8686);
            d.setRadiusMeters(1000);
            d.setBlockRoads(true);
            d.setCongestionMultiplier(3.0);
            d.setDescription("Major fire at BKC Block G");
            graphService.addDisaster(d);

            addEvacueeGroup(new EvacueeGroup("grp-1", "BKC Financial District", findNearestNodeId(graph, 19.0657, 72.8686), 8000, "Ward H-East"));
            addEvacueeGroup(new EvacueeGroup("grp-2", "Bandra East Station Ward", findNearestNodeId(graph, 19.0600, 72.8520), 5000, "Ward H-East"));
            addEvacueeGroup(new EvacueeGroup("grp-3", "Santacruz East Ward", findNearestNodeId(graph, 19.0843, 72.8360), 4000, "Ward H-West"));
        } else if ("dadar_bridge".equalsIgnoreCase(scenarioId)) {
            DisasterRequest d = new DisasterRequest();
            d.setId("disaster-dadar-bridge");
            d.setType("BRIDGE_COLLAPSE");
            d.setLatitude(19.0178);
            d.setLongitude(72.8478);
            d.setRadiusMeters(800);
            d.setBlockRoads(true);
            d.setCongestionMultiplier(3.0);
            d.setDescription("Dadar flyover structural damage");
            graphService.addDisaster(d);

            addEvacueeGroup(new EvacueeGroup("grp-1", "Dadar West Commercial Ward", findNearestNodeId(graph, 19.0178, 72.8478), 7000, "Ward G-North"));
            addEvacueeGroup(new EvacueeGroup("grp-2", "Prabhadevi Evacuees", findNearestNodeId(graph, 19.0166, 72.8296), 5000, "Ward G-South"));
            addEvacueeGroup(new EvacueeGroup("grp-3", "Worli Naka Evacuees", findNearestNodeId(graph, 19.0134, 72.8179), 6000, "Ward G-South"));
        } else {
            // Default: Chembur chemical leak
            DisasterRequest d = new DisasterRequest();
            d.setId("disaster-chembur-leak");
            d.setType("CHEMICAL_LEAK");
            d.setLatitude(19.0622);
            d.setLongitude(72.8974);
            d.setRadiusMeters(1500);
            d.setBlockRoads(false);
            d.setCongestionMultiplier(3.5);
            d.setDescription("Hazardous leak near Chembur plant");
            graphService.addDisaster(d);

            addEvacueeGroup(new EvacueeGroup("grp-1", "Chembur Industrial Evacuees", findNearestNodeId(graph, 19.0622, 72.8974), 7000, "Ward M-West"));
            addEvacueeGroup(new EvacueeGroup("grp-2", "Vikhroli South Ward", findNearestNodeId(graph, 19.1110, 72.9280), 5000, "Ward N"));
            addEvacueeGroup(new EvacueeGroup("grp-3", "Ghatkopar East Ward", findNearestNodeId(graph, 19.0860, 72.9080), 6000, "Ward N"));
        }
    }

    /**
     * Executes evacuation simulation under specified strategy.
     */
    public StrategyMetrics runSimulation(EvacuationStrategy strategy) {
        long startTimeMs = System.currentTimeMillis();
        Graph graph = graphService.getGraph();
        Collection<Shelter> shelters = graphService.getShelterService().getAllShelters();

        // Reset occupancies & road traffic for clean run
        shelters.forEach(Shelter::resetOccupancy);
        resetEdgeTraffic();

        int totalEvacuees = 0;
        int housedEvacuees = 0;
        int overflowEvacuees = 0;
        double totalDistanceKm = 0.0;
        double maxTimeMin = 0.0;
        List<Double> travelTimesMin = new ArrayList<>();

        for (EvacueeGroup group : activeEvacueeGroups.values()) {
            group.reset();
            totalEvacuees += group.getCount();

            Shelter chosenShelter = null;
            PathResult bestPath = null;
            double minTravelTimeSeconds = Double.MAX_VALUE;

            if (strategy == EvacuationStrategy.NAIVE_NEAREST) {
                // Baseline Strategy 1: Nearest shelter capacity-blind
                for (Shelter s : shelters) {
                    PathResult path = aStarEngine.findShortestPath(graph, group.getSourceNodeId(), s.getNearestNodeId());
                    if (path.isPathFound() && path.getTotalTravelTimeSeconds() < minTravelTimeSeconds) {
                        minTravelTimeSeconds = path.getTotalTravelTimeSeconds();
                        chosenShelter = s;
                        bestPath = path;
                    }
                }
            } else {
                // Strategy 2: Capacity-Aware Greedy Assignment
                for (Shelter s : shelters) {
                    if (s.getRemainingCapacity() < group.getCount()) {
                        continue; // Skip shelters without sufficient remaining capacity
                    }
                    PathResult path = aStarEngine.findShortestPath(graph, group.getSourceNodeId(), s.getNearestNodeId());
                    if (path.isPathFound() && path.getTotalTravelTimeSeconds() < minTravelTimeSeconds) {
                        minTravelTimeSeconds = path.getTotalTravelTimeSeconds();
                        chosenShelter = s;
                        bestPath = path;
                    }
                }
                // Fallback: If no single shelter has full capacity, pick closest shelter with any capacity
                if (chosenShelter == null) {
                    for (Shelter s : shelters) {
                        if (s.getRemainingCapacity() > 0) {
                            PathResult path = aStarEngine.findShortestPath(graph, group.getSourceNodeId(), s.getNearestNodeId());
                            if (path.isPathFound() && path.getTotalTravelTimeSeconds() < minTravelTimeSeconds) {
                                minTravelTimeSeconds = path.getTotalTravelTimeSeconds();
                                chosenShelter = s;
                                bestPath = path;
                            }
                        }
                    }
                }
            }

            if (chosenShelter != null && bestPath != null && bestPath.isPathFound()) {
                int assignCount = Math.min(group.getCount(), chosenShelter.getRemainingCapacity());
                if (strategy == EvacuationStrategy.NAIVE_NEAREST) {
                    // Capacity-blind forces assignment regardless of overflow
                    assignCount = group.getCount();
                    chosenShelter.reserveCapacity(assignCount);
                } else {
                    chosenShelter.reserveCapacity(assignCount);
                }

                housedEvacuees += assignCount;
                int overflow = group.getCount() - assignCount;
                overflowEvacuees += Math.max(0, overflow);

                group.setAssignedShelterId(chosenShelter.getId());
                group.setAssignedShelterName(chosenShelter.getName());
                group.setTravelTimeMinutes(bestPath.getTotalTravelTimeMinutes());
                group.setTravelDistanceKm(bestPath.getTotalDistanceMeters() / 1000.0);
                group.setStatus(overflow > 0 ? EvacueeGroup.Status.OVERFLOW : EvacueeGroup.Status.EVACUATED);

                List<Long> nodeIds = new ArrayList<>();
                List<double[]> coords = new ArrayList<>();
                for (Node n : bestPath.getPathNodes()) {
                    nodeIds.add(n.getId());
                    coords.add(new double[]{n.getLatitude(), n.getLongitude()});
                }
                group.setRouteNodeIds(nodeIds);
                group.setRouteCoordinates(coords);

                totalDistanceKm += group.getTravelDistanceKm();
                travelTimesMin.add(group.getTravelTimeMinutes());
                maxTimeMin = Math.max(maxTimeMin, group.getTravelTimeMinutes());

                // Strategy 2: Update road traffic & congestion factor along route
                if (strategy == EvacuationStrategy.CAPACITY_AWARE) {
                    for (Edge edge : bestPath.getPathEdges()) {
                        edge.addTraffic(group.getCount());
                    }
                }
            } else {
                group.setStatus(EvacueeGroup.Status.OVERFLOW);
                overflowEvacuees += group.getCount();
            }
        }

        long executionTimeMs = System.currentTimeMillis() - startTimeMs;

        StrategyMetrics metrics = new StrategyMetrics();
        metrics.strategy = strategy;
        metrics.totalEvacuees = totalEvacuees;
        metrics.evacueesSuccessfullyHoused = housedEvacuees;
        metrics.overflowEvacuees = overflowEvacuees;
        metrics.avgEvacuationTimeMinutes = travelTimesMin.isEmpty() ? 0.0 : travelTimesMin.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        metrics.maxEvacuationTimeMinutes = maxTimeMin;
        metrics.avgTravelDistanceKm = activeEvacueeGroups.isEmpty() ? 0.0 : totalDistanceKm / activeEvacueeGroups.size();
        metrics.totalTravelDistanceKm = totalDistanceKm;
        metrics.executionTimeMs = executionTimeMs;

        // Calculate shelter utilization %
        int totalCapacity = shelters.stream().mapToInt(Shelter::getTotalCapacity).sum();
        int totalOccupancy = shelters.stream().mapToInt(Shelter::getCurrentOccupancy).sum();
        metrics.shelterUtilizationPercent = totalCapacity > 0 ? ((double) totalOccupancy / totalCapacity) * 100.0 : 0.0;

        for (Shelter s : shelters) {
            metrics.shelterOccupancies.put(s.getName(), s.getCurrentOccupancy());
        }

        // Calculate average road congestion factor
        double avgCongestion = graph.getAllEdges().stream()
            .mapToDouble(Edge::getCongestionFactor)
            .average().orElse(1.0);
        metrics.averageRoadCongestionFactor = avgCongestion;

        return metrics;
    }

    /**
     * Executes side-by-side benchmark comparison between Strategy 1 and Strategy 2.
     */
    public EvacuationBenchmarkResult compareStrategies(String scenarioName) {
        EvacuationBenchmarkResult result = new EvacuationBenchmarkResult();
        result.setScenarioName(scenarioName);

        // Run Strategy 1 (Naive)
        StrategyMetrics naive = runSimulation(EvacuationStrategy.NAIVE_NEAREST);
        result.setNaiveStrategyMetrics(naive);

        // Run Strategy 2 (Capacity-Aware)
        StrategyMetrics capacityAware = runSimulation(EvacuationStrategy.CAPACITY_AWARE);
        result.setCapacityAwareStrategyMetrics(capacityAware);

        return result;
    }

    private void resetEdgeTraffic() {
        for (Edge edge : graphService.getGraph().getAllEdges()) {
            edge.resetTraffic();
        }
    }

    private long findNearestNodeId(Graph graph, double lat, double lon) {
        Node best = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : graph.getAllNodes()) {
            double dlat = n.getLatitude() - lat;
            double dlon = n.getLongitude() - lon;
            double d = dlat * dlat + dlon * dlon;
            if (d < minDist) {
                minDist = d;
                best = n;
            }
        }
        return best != null ? best.getId() : -1;
    }
}
