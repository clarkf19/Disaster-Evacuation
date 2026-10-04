package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.ShortestPathTree;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.model.Shelter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Demo mode: makes live shelter occupancy react to live hazards so the app
 * feels alive without a real check-in system.
 *
 * While hazard zones are active, each zone gets an estimated pool of people
 * who need shelter (zone area × population density × evacuating share). Every
 * tick a random slice of each pool "arrives" at the nearest safe shelters with
 * space, nearest by travel time on the hazard-aware road graph, spilling over
 * to the next shelter when one fills. People who can't be placed anywhere are
 * counted as unplaced. With no active hazards, shelters slowly empty as people
 * return home.
 *
 * This is a visual simulation, not real data — the UI labels it as such.
 */
@Service
public class DemoArrivalService {

    private static final Logger log = LoggerFactory.getLogger(DemoArrivalService.class);

    static final double PEOPLE_PER_KM2 = 25_000;   // dense urban Mumbai, order of magnitude
    static final double EVACUATING_SHARE = 0.15;   // share of residents who go to a public shelter
    static final int MAX_POOL = 60_000;

    private final GraphService graphService;
    private final ShelterService shelterService;
    private final DijkstraEngine dijkstra = new DijkstraEngine();
    private final Random random = new Random();

    private volatile boolean enabled;

    /** People still waiting to reach a shelter, per disaster id. */
    private final Map<String, Integer> pools = new HashMap<>();
    private final Map<String, Integer> unplaced = new HashMap<>();
    /** Shelter ids by travel time from each disaster, valid for one hazard snapshot. */
    private final Map<String, List<Long>> rankings = new HashMap<>();
    private HazardOverlay rankedFor;
    /** Arrivals (positive) or departures (negative) per shelter in the latest tick. */
    private volatile Map<Long, Integer> lastTickChange = Map.of();

    public DemoArrivalService(GraphService graphService, ShelterService shelterService,
                              @Value("${demo.enabled:true}") boolean enabled) {
        this.graphService = graphService;
        this.shelterService = shelterService;
        this.enabled = enabled;
        if (enabled) log.info("Demo mode ON: shelter occupancy is simulated from active hazard zones.");
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Demo mode {}", enabled ? "enabled" : "disabled");
    }

    /** People added (+) or removed (−) per shelter in the most recent tick. */
    public Map<Long, Integer> getLastTickChange() { return lastTickChange; }

    public synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("waiting", pools.values().stream().mapToInt(Integer::intValue).sum());
        m.put("unplaced", unplaced.values().stream().mapToInt(Integer::intValue).sum());
        return m;
    }

    @Scheduled(fixedDelayString = "${demo.tick-ms:4000}", initialDelayString = "${demo.tick-ms:4000}")
    public void scheduledTick() {
        if (enabled) tick();
    }

    /** One simulation step. Public so tests can drive it deterministically. */
    public synchronized void tick() {
        HazardOverlay overlay = graphService.getHazardOverlay();
        List<DisasterEvent> disasters = overlay.getDisasters();
        Map<Long, Integer> change = new HashMap<>();

        // Forget disasters that were removed; add pools for new ones.
        Set<String> activeIds = new HashSet<>();
        disasters.forEach(d -> activeIds.add(d.getId()));
        pools.keySet().retainAll(activeIds);
        unplaced.keySet().retainAll(activeIds);
        for (DisasterEvent d : disasters) pools.computeIfAbsent(d.getId(), id -> initialPool(d));

        if (disasters.isEmpty()) {
            drain(change);
        } else {
            if (overlay != rankedFor) {
                rankings.clear();
                rankedFor = overlay;
            }
            for (DisasterEvent d : disasters) arrive(d, overlay, change);
        }
        lastTickChange = Map.copyOf(change);
    }

    static int initialPool(DisasterEvent d) {
        double areaKm2 = Math.PI * Math.pow(d.getAffectedRadiusMeters() / 1000.0, 2);
        return (int) Math.min(MAX_POOL, Math.round(areaKm2 * PEOPLE_PER_KM2 * EVACUATING_SHARE));
    }

    private void arrive(DisasterEvent d, HazardOverlay overlay, Map<Long, Integer> change) {
        int waiting = pools.getOrDefault(d.getId(), 0);
        if (waiting <= 0) return;
        int initial = Math.max(1, initialPool(d));
        // 3–8 % of the original pool per tick, at least 25 people.
        int batch = Math.min(waiting, Math.max(25, (int) (initial * (0.03 + random.nextDouble() * 0.05))));

        int remaining = batch;
        for (long shelterId : ranking(d, overlay)) {
            if (remaining <= 0) break;
            Shelter s = shelterService.getShelter(shelterId).orElse(null);
            if (s == null || overlay.isShelterUnsafe(s)) continue;
            int admitted = s.admit(remaining);
            if (admitted > 0) {
                change.merge(shelterId, admitted, Integer::sum);
                remaining -= admitted;
            }
        }
        if (remaining > 0) unplaced.merge(d.getId(), remaining, Integer::sum);
        pools.put(d.getId(), waiting - batch);
    }

    /** Reachable safe shelters ordered by travel time from the disaster centre (one Dijkstra pass). */
    private List<Long> ranking(DisasterEvent d, HazardOverlay overlay) {
        return rankings.computeIfAbsent(d.getId(), id -> {
            GraphService.Snap snap = graphService.snap(d.getCenterLatitude(), d.getCenterLongitude());
            if (snap.node() == null) return List.of();
            List<Shelter> candidates = shelterService.getAllShelters().stream()
                    .filter(s -> s.getNearestNodeId() >= 0 && !overlay.isShelterUnsafe(s)).toList();
            Set<Long> targets = new HashSet<>();
            candidates.forEach(s -> targets.add(s.getNearestNodeId()));
            ShortestPathTree tree = dijkstra.searchToTargets(graphService.getGraph(), overlay.asEdgeCost(),
                    snap.node().getId(), targets);
            return candidates.stream()
                    .filter(s -> tree.reached(s.getNearestNodeId()))
                    .sorted(Comparator.comparingDouble(s -> tree.costTo(s.getNearestNodeId())))
                    .map(Shelter::getId)
                    .toList();
        });
    }

    /** No active hazards: about 5 % of each shelter's occupants go home per tick. */
    private void drain(Map<Long, Integer> change) {
        for (Shelter s : shelterService.getAllShelters()) {
            int occupancy = s.getCurrentOccupancy();
            if (occupancy == 0) continue;
            int leaving = s.release(Math.max(20, (int) Math.ceil(occupancy * 0.05)));
            if (leaving > 0) change.put(s.getId(), -leaving);
        }
    }
}
