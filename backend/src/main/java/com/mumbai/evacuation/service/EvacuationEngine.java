package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.algorithm.ShortestPathTree;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.*;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Sandboxed evacuation simulator comparing a naive baseline with a
 * capacity- and congestion-aware assignment strategy.
 *
 * Every run builds its own hazard overlay, traffic map and shelter ledger, so
 * simulations are deterministic, thread-safe, and never leak into live routing.
 *
 * Traffic model: evacuees are converted to vehicles ({@code personsPerVehicle})
 * spread over an evacuation window ({@code evacuationWindowHours}) to get a flow
 * in vehicles/hour, which is compared with each road's capacity (vehicles/hour)
 * to pick a congestion factor (see {@link Edge#congestionFactorFor}).
 *
 * NAIVE_NEAREST: each group drives to the shelter that is fastest on empty
 * roads. Nobody checks capacity; shelters admit arrivals first-come-first-served
 * and turn the rest away. Travel times are then evaluated under the traffic all
 * groups create together, so both strategies are judged by the same physics.
 *
 * CAPACITY_AWARE: groups are processed in priority order (inside a hazard zone
 * first, then largest first). Each group is sent to the fastest shelter with
 * free space given the traffic already assigned; groups that don't fit are split
 * across several shelters. After all assignments, any route whose travel time
 * grew by >= 20% because of later traffic is recomputed (re-routing pass).
 */
@Service
public class EvacuationEngine {

    static final double REROUTE_THRESHOLD = 0.20;

    private final GraphService graphService;
    private final ShelterService shelterService;
    private final double personsPerVehicle;
    private final double evacuationWindowHours;
    private final DijkstraEngine dijkstra = new DijkstraEngine();
    private final AStarEngine aStar = new AStarEngine();
    private final List<Scenario> presets;

    public record Scenario(String id, String name, String description,
                           List<DisasterEvent> disasters, List<EvacueeGroup> groups) {}

    public EvacuationEngine(GraphService graphService, ShelterService shelterService,
                            @Value("${evacuation.persons-per-vehicle:4}") double personsPerVehicle,
                            @Value("${evacuation.window-hours:3}") double evacuationWindowHours) {
        if (personsPerVehicle <= 0 || evacuationWindowHours <= 0) {
            throw new IllegalArgumentException("evacuation.persons-per-vehicle and evacuation.window-hours must be positive");
        }
        this.graphService = graphService;
        this.shelterService = shelterService;
        this.personsPerVehicle = personsPerVehicle;
        this.evacuationWindowHours = evacuationWindowHours;
        this.presets = buildPresets();
    }

    // ------------------------------------------------------------------ scenarios

    public List<Map<String, Object>> getPresetScenarios() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Scenario s : presets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("name", s.name());
            m.put("description", s.description());
            m.put("disasterTypes", s.disasters().stream().map(d -> d.getType().name()).distinct().toList());
            m.put("totalEvacuees", s.groups().stream().mapToInt(EvacueeGroup::count).sum());
            m.put("groupCount", s.groups().size());
            list.add(m);
        }
        return list;
    }

    /** Turns an API request into a scenario (preset by id, or custom groups + disasters). */
    public Scenario resolve(SimulationRequest request) {
        if (request.scenarioId() != null && !request.scenarioId().isBlank()) {
            return presets.stream().filter(s -> s.id().equalsIgnoreCase(request.scenarioId())).findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Unknown scenario: " + request.scenarioId()));
        }
        if (request.groups() == null || request.groups().isEmpty()) {
            throw new IllegalArgumentException("Provide a scenarioId or at least one evacuee group");
        }
        List<EvacueeGroup> groups = new ArrayList<>();
        int i = 1;
        for (SimulationRequest.Group g : request.groups()) {
            String id = g.id() == null || g.id().isBlank() ? "grp-" + i : g.id();
            groups.add(new EvacueeGroup(id, g.name() == null ? "Group " + i : g.name(), g.lat(), g.lon(), g.count(),
                    g.wardName() == null ? "" : g.wardName()));
            i++;
        }
        List<DisasterEvent> disasters;
        if (request.disasters() == null) {
            disasters = graphService.getActiveDisasters();
        } else {
            disasters = new ArrayList<>();
            int j = 1;
            for (DisasterRequest d : request.disasters()) {
                disasters.add(GraphService.toEvent(d.id() == null || d.id().isBlank() ? "sim-disaster-" + j : d.id(), d));
                j++;
            }
        }
        return new Scenario("custom", "Custom scenario", "User-defined evacuee groups", disasters, groups);
    }

    // ------------------------------------------------------------------ simulation

    public Comparison compare(Scenario scenario) {
        HazardOverlay overlay = HazardOverlay.build(graphService.getGraph(), scenario.disasters());
        StrategyMetrics naive = simulate(scenario, overlay, EvacuationStrategy.NAIVE_NEAREST);
        StrategyMetrics aware = simulate(scenario, overlay, EvacuationStrategy.CAPACITY_AWARE);
        return new Comparison(summarize(scenario, overlay), naive, aware);
    }

    public StrategyMetrics simulate(Scenario scenario, EvacuationStrategy strategy) {
        HazardOverlay overlay = HazardOverlay.build(graphService.getGraph(), scenario.disasters());
        return simulate(scenario, overlay, strategy);
    }

    private StrategyMetrics simulate(Scenario scenario, HazardOverlay overlay, EvacuationStrategy strategy) {
        long start = System.currentTimeMillis();
        Run run = new Run(overlay);
        if (strategy == EvacuationStrategy.NAIVE_NEAREST) {
            run.naive(scenario.groups());
        } else {
            run.capacityAware(scenario.groups());
        }
        return run.metrics(strategy, scenario.groups(), System.currentTimeMillis() - start);
    }

    private ScenarioSummary summarize(Scenario scenario, HazardOverlay overlay) {
        List<DisasterView> disasters = scenario.disasters().stream()
                .map(d -> new DisasterView(d.getId(), d.getType().name(), d.getCenterLatitude(), d.getCenterLongitude(),
                        d.getAffectedRadiusMeters(), d.isBlockRoads(), d.getCongestionMultiplier(), d.getDescription()))
                .toList();
        List<GroupInput> groups = scenario.groups().stream()
                .map(g -> new GroupInput(g.id(), g.name(), g.latitude(), g.longitude(), g.count(), g.wardName(),
                        !overlay.disastersAt(g.latitude(), g.longitude()).isEmpty()))
                .toList();
        List<Long> unsafe = shelterService.getAllShelters().stream()
                .filter(overlay::isShelterUnsafe).map(Shelter::getId).toList();
        return new ScenarioSummary(scenario.id(), scenario.name(), scenario.description(), disasters, groups, unsafe,
                personsPerVehicle, evacuationWindowHours);
    }

    /** All mutable state of one simulation run. */
    private final class Run {
        final Graph graph = graphService.getGraph();
        final HazardOverlay overlay;
        final EdgeCost hazardCost;
        final EdgeCost trafficCost;
        final Map<Long, Double> vehiclesPerHour = new HashMap<>();
        final List<SimShelter> shelters = new ArrayList<>();
        final List<Alloc> allocs = new ArrayList<>();
        final Map<String, Integer> unreachableByGroup = new HashMap<>();
        int reroutes = 0;

        Run(HazardOverlay overlay) {
            this.overlay = overlay;
            this.hazardCost = overlay.asEdgeCost();
            this.trafficCost = edge -> {
                double base = hazardCost.seconds(edge);
                return Double.isFinite(base)
                        ? base * edge.congestionFactorFor(vehiclesPerHour.getOrDefault(edge.getId(), 0.0))
                        : base;
            };
            for (Shelter s : shelterService.getAllShelters()) {
                boolean unsafe = overlay.isShelterUnsafe(s);
                shelters.add(new SimShelter(s, s.getRemainingCapacity(), unsafe));
            }
        }

        List<SimShelter> usable(boolean requireSpace) {
            return shelters.stream()
                    .filter(s -> !s.unsafe && s.nodeId >= 0 && s.capacity > 0 && (!requireSpace || s.free() > 0))
                    .toList();
        }

        List<EvacueeGroup> priorityOrder(List<EvacueeGroup> groups) {
            List<EvacueeGroup> ordered = new ArrayList<>(groups);
            ordered.sort(Comparator
                    .comparing((EvacueeGroup g) -> overlay.disastersAt(g.latitude(), g.longitude()).isEmpty())
                    .thenComparing(EvacueeGroup::count, Comparator.reverseOrder())
                    .thenComparing(EvacueeGroup::id));
            return ordered;
        }

        Long sourceNode(EvacueeGroup g) {
            GraphService.Snap snap = graphService.snap(g.latitude(), g.longitude());
            return snap.withinCoverage() ? snap.node().getId() : null;
        }

        /** Fastest reachable shelter among candidates in a single Dijkstra pass. */
        Optional<Map.Entry<SimShelter, ShortestPathTree>> nearest(long src, List<SimShelter> candidates, EdgeCost cost) {
            Set<Long> targets = new HashSet<>();
            candidates.forEach(s -> targets.add(s.nodeId));
            ShortestPathTree tree = dijkstra.searchToTargets(graph, cost, src, targets);
            return candidates.stream()
                    .filter(s -> tree.reached(s.nodeId))
                    .min(Comparator.comparingDouble((SimShelter s) -> tree.costTo(s.nodeId)).thenComparingLong(s -> s.id))
                    .map(s -> Map.entry(s, tree));
        }

        void naive(List<EvacueeGroup> groups) {
            for (EvacueeGroup g : priorityOrder(groups)) {
                Long src = sourceNode(g);
                var best = src == null ? Optional.<Map.Entry<SimShelter, ShortestPathTree>>empty()
                        : nearest(src, usable(false), hazardCost);
                if (best.isEmpty()) {
                    unreachableByGroup.merge(g.id(), g.count(), Integer::sum);
                    continue;
                }
                SimShelter shelter = best.get().getKey();
                PathResult path = best.get().getValue().pathTo(shelter.nodeId);
                shelter.arrivals += g.count();
                addTraffic(path, g.count());
                allocs.add(new Alloc(g, shelter, g.count(), path, path.getTotalTravelTimeSeconds()));
            }
            // Shelters admit arrivals in order of arrival time until full.
            evaluateTimes();
            for (SimShelter s : shelters) {
                int space = s.capacity;
                List<Alloc> arriving = allocs.stream().filter(a -> a.shelter == s)
                        .sorted(Comparator.comparingDouble(a -> a.finalSeconds)).toList();
                for (Alloc a : arriving) {
                    a.housed = Math.min(a.persons, space);
                    space -= a.housed;
                }
            }
        }

        void capacityAware(List<EvacueeGroup> groups) {
            for (EvacueeGroup g : priorityOrder(groups)) {
                Long src = sourceNode(g);
                if (src == null) {
                    unreachableByGroup.merge(g.id(), g.count(), Integer::sum);
                    continue;
                }
                int remaining = g.count();
                while (remaining > 0) {
                    List<SimShelter> open = usable(true);
                    if (open.isEmpty()) break; // all shelters full -> remaining people overflow
                    var best = nearest(src, open, trafficCost);
                    if (best.isEmpty()) {
                        unreachableByGroup.merge(g.id(), remaining, Integer::sum);
                        break;
                    }
                    SimShelter shelter = best.get().getKey();
                    PathResult path = best.get().getValue().pathTo(shelter.nodeId);
                    int persons = Math.min(remaining, shelter.free());
                    shelter.reserved += persons;
                    shelter.arrivals += persons;
                    addTraffic(path, persons);
                    Alloc alloc = new Alloc(g, shelter, persons, path, path.getTotalTravelTimeSeconds());
                    alloc.housed = persons;
                    allocs.add(alloc);
                    remaining -= persons;
                }
            }
            rerouteSlowedAllocations();
            evaluateTimes();
        }

        /** Re-route any allocation whose route became >= 20% slower due to traffic assigned after it. */
        void rerouteSlowedAllocations() {
            for (Alloc a : allocs) {
                double now = pathCost(a.path, trafficCost);
                if (now < a.secondsAtAssignment * (1 + REROUTE_THRESHOLD)) continue;
                addTraffic(a.path, -a.persons);
                long src = a.path.getPathNodes().get(0).getId();
                PathResult alternative = aStar.findShortestPath(graph, trafficCost, src, a.shelter.nodeId);
                double currentWithoutSelf = pathCost(a.path, trafficCost);
                if (alternative.isPathFound() && alternative.getTotalTravelTimeSeconds() < currentWithoutSelf - 1e-6) {
                    a.path = alternative;
                    a.rerouted = true;
                    reroutes++;
                }
                addTraffic(a.path, a.persons);
            }
        }

        void evaluateTimes() {
            for (Alloc a : allocs) a.finalSeconds = pathCost(a.path, trafficCost);
        }

        void addTraffic(PathResult path, int persons) {
            double vph = persons / personsPerVehicle / evacuationWindowHours;
            for (Edge e : path.getPathEdges()) {
                vehiclesPerHour.merge(e.getId(), vph, (x, y) -> Math.max(0.0, x + y));
            }
        }

        double pathCost(PathResult path, EdgeCost cost) {
            double total = 0;
            for (Edge e : path.getPathEdges()) total += cost.seconds(e);
            return total;
        }

        StrategyMetrics metrics(EvacuationStrategy strategy, List<EvacueeGroup> groups, long execMs) {
            int total = groups.stream().mapToInt(EvacueeGroup::count).sum();
            int housed = allocs.stream().mapToInt(a -> a.housed).sum();
            int unreachable = unreachableByGroup.values().stream().mapToInt(Integer::intValue).sum();

            double weightedTime = 0, weightedDist = 0, maxTime = 0;
            for (Alloc a : allocs) {
                if (a.housed <= 0) continue;
                double minutes = a.finalSeconds / 60.0;
                weightedTime += a.housed * minutes;
                weightedDist += a.housed * a.path.getTotalDistanceMeters() / 1000.0;
                maxTime = Math.max(maxTime, minutes);
            }
            int usableCapacity = usable(false).stream().mapToInt(s -> s.capacity).sum();

            double congestedKm = 0;
            for (Map.Entry<Long, Double> entry : vehiclesPerHour.entrySet()) {
                Edge e = graph.getEdge(entry.getKey());
                if (e.congestionFactorFor(entry.getValue()) >= 1.7) congestedKm += e.getDistanceMeters() / 1000.0;
            }

            List<ShelterLoad> loads = new ArrayList<>();
            int overCapacity = 0;
            for (SimShelter s : shelters) {
                int shelterHoused = allocs.stream().filter(a -> a.shelter == s).mapToInt(a -> a.housed).sum();
                if (s.arrivals > s.capacity) overCapacity++;
                loads.add(new ShelterLoad(s.id, s.name, s.capacity, s.arrivals, shelterHoused, s.unsafe));
            }

            List<Allocation> allocations = allocs.stream()
                    .map(a -> new Allocation(a.group.id(), a.group.name(), a.shelter.id, a.shelter.name, a.persons, a.housed,
                            round(a.finalSeconds / 60.0), round(a.path.getTotalDistanceMeters() / 1000.0), a.rerouted,
                            a.path.getPathNodes().stream().map(n -> new double[]{n.getLatitude(), n.getLongitude()}).toList()))
                    .toList();

            List<GroupOutcome> outcomes = new ArrayList<>();
            for (EvacueeGroup g : groups) {
                int groupHoused = allocs.stream().filter(a -> a.group.id().equals(g.id())).mapToInt(a -> a.housed).sum();
                int overflow = g.count() - groupHoused;
                String status = overflow == 0 ? "EVACUATED"
                        : groupHoused > 0 ? "PARTIAL"
                        : unreachableByGroup.containsKey(g.id()) ? "UNREACHABLE" : "OVERFLOW";
                outcomes.add(new GroupOutcome(g.id(), g.name(), g.count(), groupHoused, overflow, status));
            }

            return new StrategyMetrics(strategy, total, housed, total - housed, unreachable,
                    housed > 0 ? round(weightedTime / housed) : 0, round(maxTime),
                    housed > 0 ? round(weightedDist / housed) : 0,
                    usableCapacity > 0 ? round(100.0 * housed / usableCapacity) : 0,
                    overCapacity, round(congestedKm), reroutes, execMs, loads, allocations, outcomes);
        }
    }

    private static final class SimShelter {
        final long id;
        final String name;
        final long nodeId;
        final int capacity;
        final boolean unsafe;
        int reserved;
        int arrivals;

        SimShelter(Shelter s, int capacity, boolean unsafe) {
            this.id = s.getId();
            this.name = s.getName();
            this.nodeId = s.getNearestNodeId();
            this.capacity = capacity;
            this.unsafe = unsafe;
        }

        int free() { return capacity - reserved; }
    }

    private static final class Alloc {
        final EvacueeGroup group;
        final SimShelter shelter;
        final int persons;
        final double secondsAtAssignment;
        PathResult path;
        int housed;
        double finalSeconds;
        boolean rerouted;

        Alloc(EvacueeGroup group, SimShelter shelter, int persons, PathResult path, double secondsAtAssignment) {
            this.group = group;
            this.shelter = shelter;
            this.persons = persons;
            this.path = path;
            this.secondsAtAssignment = secondsAtAssignment;
        }
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    // ------------------------------------------------------------------ presets

    private static List<Scenario> buildPresets() {
        return List.of(
            new Scenario("sion_flood", "Sion Monsoon Heavy Flood",
                "Severe urban flooding at Sion Circle blocking the Sion interchange. Evacuating Sion Koliwada, Kurla West and Dadar East.",
                List.of(event("sim-sion-flood", DisasterType.FLOOD, 19.0390, 72.8619, 1200, true, 3.0, "Monsoon flooding at Sion Circle")),
                List.of(new EvacueeGroup("grp-1", "Sion Koliwada Evacuees", 19.0390, 72.8619, 5000, "Ward F-North"),
                        new EvacueeGroup("grp-2", "Kurla West Evacuees", 19.0650, 72.8790, 6000, "Ward L"),
                        new EvacueeGroup("grp-3", "Dadar East Evacuees", 19.0178, 72.8478, 4000, "Ward F-South"))),
            new Scenario("bkc_fire", "Bandra-Kurla Complex Major Fire",
                "Commercial building fire at BKC. Evacuating 17,000 people from BKC, Bandra East and Santacruz.",
                List.of(event("sim-bkc-fire", DisasterType.FIRE, 19.0657, 72.8686, 1000, true, 3.0, "Major fire at BKC Block G")),
                List.of(new EvacueeGroup("grp-1", "BKC Financial District", 19.0657, 72.8686, 8000, "Ward H-East"),
                        new EvacueeGroup("grp-2", "Bandra East Station Ward", 19.0600, 72.8520, 5000, "Ward H-East"),
                        new EvacueeGroup("grp-3", "Santacruz East Ward", 19.0843, 72.8360, 4000, "Ward H-West"))),
            new Scenario("dadar_bridge", "Dadar Bridge Collapse",
                "Bridge collapse at the Dadar West intersection freezing traffic across South-Central Mumbai.",
                List.of(event("sim-dadar-bridge", DisasterType.BRIDGE_COLLAPSE, 19.0178, 72.8478, 800, true, 3.0, "Dadar flyover structural failure")),
                List.of(new EvacueeGroup("grp-1", "Dadar West Commercial Ward", 19.0178, 72.8478, 7000, "Ward G-North"),
                        new EvacueeGroup("grp-2", "Prabhadevi Evacuees", 19.0166, 72.8296, 5000, "Ward G-South"),
                        new EvacueeGroup("grp-3", "Worli Naka Evacuees", 19.0134, 72.8179, 6000, "Ward G-South"))),
            new Scenario("chembur_leak", "Chembur Chemical Leak",
                "Hazardous chemical release near the Chembur industrial corridor; roads stay open but slow to 3.5x travel time.",
                List.of(event("sim-chembur-leak", DisasterType.CHEMICAL_LEAK, 19.0622, 72.8974, 1500, false, 3.5, "Hazardous leak near Chembur plant")),
                List.of(new EvacueeGroup("grp-1", "Chembur Industrial Evacuees", 19.0622, 72.8974, 7000, "Ward M-West"),
                        new EvacueeGroup("grp-2", "Vikhroli South Ward", 19.1110, 72.9280, 5000, "Ward N"),
                        new EvacueeGroup("grp-3", "Ghatkopar East Ward", 19.0860, 72.9080, 6000, "Ward N"))),
            new Scenario("western_suburbs_flood", "Western Suburbs Multi-Point Flood (stress test)",
                "Illustrative stress test: three simultaneous flood zones in the western suburbs with 82,000 evacuees — "
                    + "more than the nearest shelters can hold, which is where capacity-aware assignment matters most.",
                List.of(event("sim-andheri-flood", DisasterType.FLOOD, 19.1197, 72.8464, 1000, true, 3.0, "Andheri subway flooding"),
                        event("sim-vileparle-flood", DisasterType.FLOOD, 19.0990, 72.8440, 700, true, 3.0, "Vile Parle low-lying area flooding"),
                        event("sim-malad-flood", DisasterType.FLOOD, 19.1860, 72.8485, 900, true, 3.0, "Malad subway flooding")),
                List.of(new EvacueeGroup("grp-1", "Andheri West Residents", 19.1197, 72.8464, 30000, "Ward K-West"),
                        new EvacueeGroup("grp-2", "Vile Parle Residents", 19.0990, 72.8440, 12000, "Ward K-East"),
                        new EvacueeGroup("grp-3", "Malad West Residents", 19.1860, 72.8485, 15000, "Ward P-North"),
                        new EvacueeGroup("grp-4", "Goregaon West Residents", 19.1663, 72.8454, 25000, "Ward P-South")))
        );
    }

    private static DisasterEvent event(String id, DisasterType type, double lat, double lon, double radius,
                                       boolean block, double multiplier, String description) {
        return new DisasterEvent(id, type, lat, lon, radius, block, multiplier, description);
    }
}
