package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.CostModel;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.algorithm.ShortestPathTree;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.*;
import com.mumbai.evacuation.dto.SimulationParams;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Sandboxed evacuation simulator comparing a naive baseline with a
 * capacity- and congestion-aware assignment strategy, for a mix of travel modes.
 *
 * Every run builds its own load map and shelter ledger, so simulations are
 * deterministic, thread-safe (they can run in parallel), and never leak into
 * live routing.
 *
 * Modes: each group is split into walkers, train riders (walk + suburban rail)
 * and drivers according to {@link SimulationParams}. Drivers load roads
 * (people / persons-per-vehicle / window → vehicles/hour vs. road capacity);
 * train riders load rail links (people / window → persons/hour vs. line
 * capacity); walkers are assumed not to congest roads.
 *
 * NAIVE_NEAREST: each sub-group goes to the shelter that is fastest on empty
 * roads/trains. Nobody checks capacity; shelters admit arrivals first-come-
 * first-served and turn the rest away. Travel times are then evaluated under
 * the load all sub-groups create together, so both strategies are judged by the
 * same physics.
 *
 * CAPACITY_AWARE: groups are processed in priority order (inside a hazard zone
 * first, then largest first). Each sub-group goes to the fastest shelter with
 * free space given the load already assigned, splitting across shelters when
 * needed. After all assignments, any route whose travel time grew by >= 20%
 * because of later load is recomputed (re-routing pass).
 */
@Service
public class EvacuationEngine {

    static final double REROUTE_THRESHOLD = 0.20;
    private static final List<TravelMode> MODE_ORDER = List.of(TravelMode.WALK, TravelMode.TRANSIT, TravelMode.DRIVE);

    private final GraphService graphService;
    private final ShelterService shelterService;
    private final SimulationParams defaults;
    private final DijkstraEngine dijkstra = new DijkstraEngine();
    private final AStarEngine aStar = new AStarEngine();
    private final List<Scenario> presets;

    public record Scenario(String id, String name, String description,
                           List<DisasterEvent> disasters, List<EvacueeGroup> groups) {}

    public EvacuationEngine(GraphService graphService, ShelterService shelterService, FloodHotspotService hotspots,
                            @Value("${evacuation.persons-per-vehicle:4}") double personsPerVehicle,
                            @Value("${evacuation.window-hours:3}") double evacuationWindowHours,
                            @Value("${evacuation.walk-share:0.5}") double walkShare,
                            @Value("${evacuation.transit-share:0.3}") double transitShare) {
        this.graphService = graphService;
        this.shelterService = shelterService;
        this.defaults = new SimulationParams(personsPerVehicle, evacuationWindowHours, 1.0, walkShare, transitShare);
        this.presets = buildPresets(hotspots);
    }

    public SimulationParams getDefaults() { return defaults; }

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

    public Scenario preset(String id) {
        return presets.stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown scenario: " + id));
    }

    /** Turns an API request into a scenario (preset by id, or custom groups + disasters). */
    public Scenario resolve(SimulationRequest request) {
        if (request.scenarioId() != null && !request.scenarioId().isBlank()) {
            return preset(request.scenarioId());
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

    public Comparison compare(Scenario scenario, SimulationParams params) {
        HazardOverlay overlay = overlayFor(scenario);
        StrategyMetrics naive = simulate(scenario, overlay, EvacuationStrategy.NAIVE_NEAREST, params);
        StrategyMetrics aware = simulate(scenario, overlay, EvacuationStrategy.CAPACITY_AWARE, params);
        return new Comparison(summarize(scenario, overlay, params), naive, aware);
    }

    public Comparison compare(Scenario scenario) {
        return compare(scenario, defaults);
    }

    public StrategyMetrics simulate(Scenario scenario, EvacuationStrategy strategy, SimulationParams params) {
        return simulate(scenario, overlayFor(scenario), strategy, params);
    }

    public StrategyMetrics simulate(Scenario scenario, EvacuationStrategy strategy) {
        return simulate(scenario, strategy, defaults);
    }

    public HazardOverlay overlayFor(Scenario scenario) {
        return HazardOverlay.build(graphService.getGraph(), scenario.disasters());
    }

    /** Runs one strategy against a prebuilt overlay (lets evaluations reuse it across many runs). */
    public StrategyMetrics simulate(Scenario scenario, HazardOverlay overlay, EvacuationStrategy strategy, SimulationParams params) {
        long start = System.currentTimeMillis();
        Run run = new Run(overlay, params);
        if (strategy == EvacuationStrategy.NAIVE_NEAREST) {
            run.naive(scenario.groups());
        } else {
            run.capacityAware(scenario.groups());
        }
        return run.metrics(strategy, scenario.groups(), System.currentTimeMillis() - start);
    }

    private ScenarioSummary summarize(Scenario scenario, HazardOverlay overlay, SimulationParams params) {
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
        return new ScenarioSummary(scenario.id(), scenario.name(), scenario.description(), disasters, groups, unsafe, params);
    }

    /** One group's share travelling by one mode. */
    private record SubGroup(EvacueeGroup group, TravelMode mode, int persons) {}

    /** All mutable state of one simulation run. */
    private final class Run {
        final Graph graph = graphService.getGraph();
        final HazardOverlay overlay;
        final SimulationParams params;
        final Map<Long, Double> load = new HashMap<>();
        final EnumMap<TravelMode, EdgeCost> emptyCost = new EnumMap<>(TravelMode.class);
        final EnumMap<TravelMode, EdgeCost> loadedCost = new EnumMap<>(TravelMode.class);
        final List<SimShelter> shelters = new ArrayList<>();
        final Map<Long, List<SimShelter>> sheltersByNode = new HashMap<>();
        final List<Alloc> allocs = new ArrayList<>();
        final Map<String, Integer> unreachableByGroup = new HashMap<>();
        final Map<String, Long> sourceNodes = new HashMap<>();
        int reroutes = 0;

        Run(HazardOverlay overlay, SimulationParams params) {
            this.overlay = overlay;
            this.params = params;
            for (TravelMode m : TravelMode.values()) {
                emptyCost.put(m, CostModel.of(m, overlay));
                loadedCost.put(m, CostModel.of(m, overlay, load));
            }
            for (Shelter s : shelterService.getAllShelters()) {
                int capacity = (int) Math.floor(s.getRemainingCapacity() * params.capacityScale());
                SimShelter sim = new SimShelter(s, capacity, overlay.isShelterUnsafe(s));
                shelters.add(sim);
                if (sim.usable()) sheltersByNode.computeIfAbsent(sim.nodeId, k -> new ArrayList<>()).add(sim);
            }
        }

        List<SubGroup> split(List<EvacueeGroup> groups) {
            List<EvacueeGroup> ordered = new ArrayList<>(groups);
            ordered.sort(Comparator
                    .comparing((EvacueeGroup g) -> overlay.disastersAt(g.latitude(), g.longitude()).isEmpty())
                    .thenComparing(EvacueeGroup::count, Comparator.reverseOrder())
                    .thenComparing(EvacueeGroup::id));
            List<SubGroup> subs = new ArrayList<>();
            for (EvacueeGroup g : ordered) {
                int walk = (int) Math.round(g.count() * params.walkShare());
                int transit = Math.min(g.count() - walk, (int) Math.round(g.count() * params.transitShare()));
                int drive = g.count() - walk - transit;
                for (TravelMode m : MODE_ORDER) {
                    int n = switch (m) { case WALK -> walk; case TRANSIT -> transit; case DRIVE -> drive; };
                    if (n > 0) subs.add(new SubGroup(g, m, n));
                }
            }
            return subs;
        }

        Long sourceNode(EvacueeGroup g) {
            return sourceNodes.computeIfAbsent(g.id(), id -> {
                GraphService.Snap snap = graphService.snap(g.latitude(), g.longitude());
                return snap.withinCoverage() ? snap.node().getId() : null;
            });
        }

        /** Nearest shelter (by the given cost) among those accepted by {@code filter}, in one early-stopping Dijkstra pass. */
        Optional<Map.Entry<SimShelter, PathResult>> nearest(long src, EdgeCost cost, java.util.function.Predicate<SimShelter> filter) {
            Set<Long> targets = new HashSet<>();
            for (Map.Entry<Long, List<SimShelter>> e : sheltersByNode.entrySet()) {
                if (e.getValue().stream().anyMatch(filter)) targets.add(e.getKey());
            }
            if (targets.isEmpty()) return Optional.empty();
            ShortestPathTree tree = dijkstra.searchToTargets(graph, cost, src, targets, 1);
            for (long node : targets) {
                if (!tree.reached(node)) continue;
                SimShelter s = sheltersByNode.get(node).stream().filter(filter)
                        .min(Comparator.comparingLong(x -> x.id)).orElseThrow();
                return Optional.of(Map.entry(s, tree.pathTo(node)));
            }
            return Optional.empty();
        }

        void naive(List<EvacueeGroup> groups) {
            for (SubGroup sg : split(groups)) {
                Long src = sourceNode(sg.group());
                var best = src == null ? Optional.<Map.Entry<SimShelter, PathResult>>empty()
                        : nearest(src, emptyCost.get(sg.mode()), s -> true);
                if (best.isEmpty()) {
                    unreachableByGroup.merge(sg.group().id(), sg.persons(), Integer::sum);
                    continue;
                }
                SimShelter shelter = best.get().getKey();
                PathResult path = best.get().getValue();
                shelter.arrivals += sg.persons();
                addLoad(path, sg.mode(), sg.persons());
                allocs.add(new Alloc(sg, shelter, sg.persons(), path, path.getTotalTravelTimeSeconds()));
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
            for (SubGroup sg : split(groups)) {
                Long src = sourceNode(sg.group());
                if (src == null) {
                    unreachableByGroup.merge(sg.group().id(), sg.persons(), Integer::sum);
                    continue;
                }
                int remaining = sg.persons();
                while (remaining > 0) {
                    var best = nearest(src, loadedCost.get(sg.mode()), s -> s.free() > 0);
                    if (best.isEmpty()) {
                        boolean anySpace = shelters.stream().anyMatch(s -> s.usable() && s.free() > 0);
                        if (anySpace) unreachableByGroup.merge(sg.group().id(), remaining, Integer::sum);
                        break; // otherwise every shelter is full: the rest overflow
                    }
                    SimShelter shelter = best.get().getKey();
                    PathResult path = best.get().getValue();
                    int persons = Math.min(remaining, shelter.free());
                    shelter.reserved += persons;
                    shelter.arrivals += persons;
                    addLoad(path, sg.mode(), persons);
                    Alloc alloc = new Alloc(sg, shelter, persons, path, path.getTotalTravelTimeSeconds());
                    alloc.housed = persons;
                    allocs.add(alloc);
                    remaining -= persons;
                }
            }
            rerouteSlowedAllocations();
            evaluateTimes();
        }

        /** Re-route any allocation whose route became >= 20% slower due to load assigned after it. */
        void rerouteSlowedAllocations() {
            for (Alloc a : allocs) {
                TravelMode mode = a.sub.mode();
                EdgeCost cost = loadedCost.get(mode);
                double now = pathCost(a.path, cost);
                if (now < a.secondsAtAssignment * (1 + REROUTE_THRESHOLD)) continue;
                addLoad(a.path, mode, -a.persons);
                long src = a.path.getPathNodes().get(0).getId();
                PathResult alternative = aStar.findShortestPath(graph, cost, src, a.shelter.nodeId, mode.maxSpeedKmH(graph));
                double currentWithoutSelf = pathCost(a.path, cost);
                if (alternative.isPathFound() && alternative.getTotalTravelTimeSeconds() < currentWithoutSelf - 1e-6) {
                    a.path = alternative;
                    a.rerouted = true;
                    reroutes++;
                }
                addLoad(a.path, mode, a.persons);
            }
        }

        void evaluateTimes() {
            for (Alloc a : allocs) a.finalSeconds = pathCost(a.path, loadedCost.get(a.sub.mode()));
        }

        void addLoad(PathResult path, TravelMode mode, int persons) {
            for (Edge e : path.getPathEdges()) {
                if (!CostModel.congestible(mode, e)) continue;
                double perHour = e.getKind() == Edge.Kind.RAIL
                        ? persons / params.evacuationWindowHours()
                        : persons / params.personsPerVehicle() / params.evacuationWindowHours();
                load.merge(e.getId(), perHour, (x, y) -> Math.max(0.0, x + y));
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
            Map<TravelMode, double[]> modeAcc = new EnumMap<>(TravelMode.class); // persons, housed, weighted minutes
            for (SubGroup sg : split(groups)) modeAcc.computeIfAbsent(sg.mode(), m -> new double[3])[0] += sg.persons();
            for (Alloc a : allocs) {
                if (a.housed <= 0) continue;
                double minutes = a.finalSeconds / 60.0;
                weightedTime += a.housed * minutes;
                weightedDist += a.housed * a.path.getTotalDistanceMeters() / 1000.0;
                maxTime = Math.max(maxTime, minutes);
                double[] acc = modeAcc.computeIfAbsent(a.sub.mode(), m -> new double[3]);
                acc[1] += a.housed;
                acc[2] += a.housed * minutes;
            }
            int usableCapacity = shelters.stream().filter(SimShelter::usable).mapToInt(s -> s.capacity).sum();

            double congestedKm = 0, crowdedRailKm = 0;
            for (Map.Entry<Long, Double> entry : load.entrySet()) {
                Edge e = graph.getEdge(entry.getKey());
                if (e.congestionFactorFor(entry.getValue()) < 1.7) continue;
                if (e.getKind() == Edge.Kind.RAIL) crowdedRailKm += e.getDistanceMeters() / 1000.0;
                else congestedKm += e.getDistanceMeters() / 1000.0;
            }

            List<ShelterLoad> loads = new ArrayList<>();
            int overCapacity = 0;
            Map<SimShelter, Integer> housedByShelter = new HashMap<>();
            for (Alloc a : allocs) housedByShelter.merge(a.shelter, a.housed, Integer::sum);
            for (SimShelter s : shelters) {
                if (s.arrivals > s.capacity) overCapacity++;
                if (s.arrivals > 0 || s.unsafe) {
                    loads.add(new ShelterLoad(s.id, s.name, s.capacity, s.arrivals, housedByShelter.getOrDefault(s, 0), s.unsafe));
                }
            }

            List<Allocation> allocations = allocs.stream()
                    .map(a -> new Allocation(a.sub.group().id(), a.sub.group().name(), a.sub.mode().name(), a.shelter.id,
                            a.shelter.name, a.persons, a.housed, round(a.finalSeconds / 60.0),
                            round(a.path.getTotalDistanceMeters() / 1000.0), a.rerouted,
                            a.path.getPathNodes().stream().map(n -> new double[]{n.getLatitude(), n.getLongitude()}).toList()))
                    .toList();

            List<GroupOutcome> outcomes = new ArrayList<>();
            for (EvacueeGroup g : groups) {
                int groupHoused = allocs.stream().filter(a -> a.sub.group().id().equals(g.id())).mapToInt(a -> a.housed).sum();
                int overflow = g.count() - groupHoused;
                String status = overflow == 0 ? "EVACUATED"
                        : groupHoused > 0 ? "PARTIAL"
                        : unreachableByGroup.containsKey(g.id()) ? "UNREACHABLE" : "OVERFLOW";
                outcomes.add(new GroupOutcome(g.id(), g.name(), g.count(), groupHoused, overflow, status));
            }

            Map<String, ModeStats> byMode = new LinkedHashMap<>();
            for (TravelMode m : MODE_ORDER) {
                double[] acc = modeAcc.get(m);
                if (acc == null) continue;
                byMode.put(m.name(), new ModeStats((int) acc[0], (int) acc[1], acc[1] > 0 ? round(acc[2] / acc[1]) : 0));
            }

            return new StrategyMetrics(strategy, total, housed, total - housed, unreachable,
                    total > 0 ? round(100.0 * housed / total) : 0,
                    housed > 0 ? round(weightedTime / housed) : 0, round(maxTime),
                    housed > 0 ? round(weightedDist / housed) : 0,
                    usableCapacity > 0 ? round(100.0 * housed / usableCapacity) : 0,
                    overCapacity, round(congestedKm), round(crowdedRailKm), reroutes, execMs,
                    byMode, loads, allocations, outcomes);
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

        boolean usable() { return !unsafe && nodeId >= 0 && capacity > 0; }
        int free() { return capacity - reserved; }
    }

    private static final class Alloc {
        final SubGroup sub;
        final SimShelter shelter;
        final int persons;
        final double secondsAtAssignment;
        PathResult path;
        int housed;
        double finalSeconds;
        boolean rerouted;

        Alloc(SubGroup sub, SimShelter shelter, int persons, PathResult path, double secondsAtAssignment) {
            this.sub = sub;
            this.shelter = shelter;
            this.persons = persons;
            this.path = path;
            this.secondsAtAssignment = secondsAtAssignment;
        }
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    // ------------------------------------------------------------------ presets

    private static List<Scenario> buildPresets(FloodHotspotService hotspots) {
        List<Scenario> list = new ArrayList<>(List.of(
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
        ));
        List<DisasterEvent> monsoon = hotspots.asFloodEvents("sim-");
        if (!monsoon.isEmpty()) {
            List<EvacueeGroup> residents = new ArrayList<>();
            int i = 1;
            for (FloodHotspotService.Hotspot h : hotspots.getHotspots()) {
                residents.add(new EvacueeGroup("grp-" + i++, "Residents near " + h.name(), h.lat(), h.lon(), 3000, ""));
            }
            list.add(new Scenario("monsoon_hotspots", "Monsoon: all chronic flooding spots",
                    "Heavy monsoon day: every chronic water-logging spot floods at once and low-lying roads nearby are slowed. "
                        + "3,000 residents evacuate from around each spot.", monsoon, residents));
        }
        return List.copyOf(list);
    }

    private static DisasterEvent event(String id, DisasterType type, double lat, double lon, double radius,
                                       boolean block, double multiplier, String description) {
        return new DisasterEvent(id, type, lat, lon, radius, block, multiplier, description);
    }
}
