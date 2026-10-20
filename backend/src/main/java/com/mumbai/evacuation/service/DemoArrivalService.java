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
 * An <b>evacuation drill</b> does the same without touching live routing:
 * residents around every chronic monsoon flooding spot head to their nearest
 * shelters, the shelters stay full for a while, then everyone goes home.
 *
 * This is a visual simulation, not real data — the UI labels it as such.
 */
@Service
public class DemoArrivalService {

    private static final Logger log = LoggerFactory.getLogger(DemoArrivalService.class);

    static final double PEOPLE_PER_KM2 = 25_000;   // dense urban Mumbai, order of magnitude
    static final double EVACUATING_SHARE = 0.15;   // share of residents who go to a public shelter
    static final int MAX_POOL = 60_000;
    /** Residents evacuating from around each monsoon hotspot during a drill. */
    static final int DRILL_PEOPLE_PER_SPOT = 3_000;
    /** How long shelters stay occupied after the last drill arrivals, before people go home. */
    static final long DRILL_HOLD_MS = 90_000;

    private final GraphService graphService;
    private final ShelterService shelterService;
    private final FloodHotspotService hotspotService;
    private final DijkstraEngine dijkstra = new DijkstraEngine();
    private final Random random = new Random();

    private volatile boolean enabled;

    /** One place people leave from: a live hazard zone or a drill hotspot. */
    private record Origin(String id, String name, double lat, double lon) {}

    /** People still waiting to reach a shelter, per origin id. */
    private final Map<String, Integer> pools = new HashMap<>();
    private final Map<String, Integer> initialPools = new HashMap<>();
    private final Map<String, Integer> unplaced = new HashMap<>();
    /** Shelter ids by travel time from each origin, valid for one hazard snapshot. */
    private final Map<String, List<Long>> rankings = new HashMap<>();
    private HazardOverlay rankedFor;
    /** Arrivals (positive) or departures (negative) per shelter in the latest tick. */
    private volatile Map<Long, Integer> lastTickChange = Map.of();

    /** Drill origins (empty when no drill is running) and when the last drill arrivals happened. */
    private List<Origin> drillOrigins = List.of();
    private long drillSettledAt = -1;
    private int drillTotal;

    public DemoArrivalService(GraphService graphService, ShelterService shelterService,
                              FloodHotspotService hotspotService,
                              @Value("${demo.enabled:true}") boolean enabled) {
        this.graphService = graphService;
        this.shelterService = shelterService;
        this.hotspotService = hotspotService;
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
        m.put("sheltered", shelterService.getAllShelters().stream().mapToInt(Shelter::getCurrentOccupancy).sum());
        Map<String, Object> drill = new LinkedHashMap<>();
        drill.put("active", isDrillActive());
        drill.put("phase", drillPhase());
        drill.put("evacuees", drillTotal);
        drill.put("origins", drillOrigins.size());
        m.put("drill", drill);
        return m;
    }

    /**
     * Starts an evacuation drill: residents around every chronic monsoon flooding spot head to
     * their nearest safe shelters. Also switches demo mode on, since the drill runs on its ticks.
     */
    public synchronized Map<String, Object> startDrill() {
        stopDrillInternal();
        List<Origin> origins = hotspotService.getHotspots().stream()
                .map(h -> new Origin("drill-" + h.id(), h.name(), h.lat(), h.lon()))
                .toList();
        for (Origin o : origins) {
            pools.put(o.id(), DRILL_PEOPLE_PER_SPOT);
            initialPools.put(o.id(), DRILL_PEOPLE_PER_SPOT);
        }
        drillOrigins = origins;
        drillTotal = origins.size() * DRILL_PEOPLE_PER_SPOT;
        drillSettledAt = -1;
        enabled = true;
        log.info("Evacuation drill started: {} people from {} flooding spots", drillTotal, origins.size());
        tick(); // first arrivals straight away
        return status();
    }

    /** Ends the drill; people already in shelters go home over the next ticks. */
    public synchronized Map<String, Object> stopDrill() {
        stopDrillInternal();
        return status();
    }

    private void stopDrillInternal() {
        for (Origin o : drillOrigins) {
            pools.remove(o.id());
            initialPools.remove(o.id());
            unplaced.remove(o.id());
            rankings.remove(o.id());
        }
        drillOrigins = List.of();
        drillSettledAt = -1;
        drillTotal = 0;
    }

    private boolean isDrillActive() { return !drillOrigins.isEmpty(); }

    /** ARRIVING while people are on their way, SHELTERED while shelters hold them, or NONE. */
    private String drillPhase() {
        if (!isDrillActive()) return "NONE";
        return drillSettledAt < 0 ? "ARRIVING" : "SHELTERED";
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

        // Forget hazards that were removed; add pools for new ones.
        Set<String> activeIds = new HashSet<>();
        disasters.forEach(d -> activeIds.add(d.getId()));
        drillOrigins.forEach(o -> activeIds.add(o.id()));
        pools.keySet().retainAll(activeIds);
        initialPools.keySet().retainAll(activeIds);
        unplaced.keySet().retainAll(activeIds);
        for (DisasterEvent d : disasters) {
            if (!pools.containsKey(d.getId())) {
                int pool = initialPool(d);
                pools.put(d.getId(), pool);
                initialPools.put(d.getId(), pool);
            }
        }

        if (overlay != rankedFor) {
            rankings.clear();
            rankedFor = overlay;
        }
        List<Origin> origins = new ArrayList<>();
        for (DisasterEvent d : disasters) {
            origins.add(new Origin(d.getId(), d.getDescription(), d.getCenterLatitude(), d.getCenterLongitude()));
        }
        origins.addAll(drillOrigins);

        if (origins.isEmpty()) {
            drain(change);
        } else {
            for (Origin o : origins) arrive(o, overlay, change);
        }
        updateDrill();
        lastTickChange = Map.copyOf(change);
    }

    /** Once every drill evacuee has arrived (or been turned away), hold, then send everyone home. */
    private void updateDrill() {
        if (!isDrillActive()) return;
        boolean everyoneMoved = drillOrigins.stream().allMatch(o -> pools.getOrDefault(o.id(), 0) <= 0);
        if (everyoneMoved && drillSettledAt < 0) drillSettledAt = System.currentTimeMillis();
        if (drillSettledAt >= 0 && System.currentTimeMillis() - drillSettledAt >= DRILL_HOLD_MS) {
            log.info("Evacuation drill finished; shelters will empty");
            stopDrillInternal();
        }
    }

    static int initialPool(DisasterEvent d) {
        double areaKm2 = Math.PI * Math.pow(d.getAffectedRadiusMeters() / 1000.0, 2);
        return (int) Math.min(MAX_POOL, Math.round(areaKm2 * PEOPLE_PER_KM2 * EVACUATING_SHARE));
    }

    private void arrive(Origin origin, HazardOverlay overlay, Map<Long, Integer> change) {
        int waiting = pools.getOrDefault(origin.id(), 0);
        if (waiting <= 0) return;
        int initial = Math.max(1, initialPools.getOrDefault(origin.id(), waiting));
        // 3–8 % of the original pool per tick, at least 25 people.
        int batch = Math.min(waiting, Math.max(25, (int) (initial * (0.03 + random.nextDouble() * 0.05))));

        int remaining = batch;
        for (long shelterId : ranking(origin, overlay)) {
            if (remaining <= 0) break;
            Shelter s = shelterService.getShelter(shelterId).orElse(null);
            if (s == null || overlay.isShelterUnsafe(s)) continue;
            int admitted = s.admit(remaining);
            if (admitted > 0) {
                change.merge(shelterId, admitted, Integer::sum);
                remaining -= admitted;
            }
        }
        if (remaining > 0) unplaced.merge(origin.id(), remaining, Integer::sum);
        pools.put(origin.id(), waiting - batch);
    }

    /** Reachable safe shelters ordered by travel time from the origin (one Dijkstra pass). */
    private List<Long> ranking(Origin origin, HazardOverlay overlay) {
        return rankings.computeIfAbsent(origin.id(), id -> {
            GraphService.Snap snap = graphService.snap(origin.lat(), origin.lon());
            if (snap.node() == null) return List.of();
            List<Shelter> candidates = shelterService.getAllShelters().stream()
                    .filter(s -> s.getNearestNodeId() >= 0 && !overlay.isShelterUnsafe(s)).toList();
            Set<Long> targets = new HashSet<>();
            candidates.forEach(s -> targets.add(s.getNearestNodeId()));
            // The 40 nearest shelters are plenty to spill over into.
            ShortestPathTree tree = dijkstra.searchToTargets(graphService.getGraph(), overlay.asEdgeCost(),
                    snap.node().getId(), targets, 40);
            return candidates.stream()
                    .filter(s -> tree.reached(s.getNearestNodeId()))
                    .sorted(Comparator.comparingDouble(s -> tree.costTo(s.getNearestNodeId())))
                    .map(Shelter::getId)
                    .toList();
        });
    }

    /** Nothing driving arrivals: about 5 % of each shelter's occupants go home per tick. */
    private void drain(Map<Long, Integer> change) {
        for (Shelter s : shelterService.getAllShelters()) {
            int occupancy = s.getCurrentOccupancy();
            if (occupancy == 0) continue;
            int leaving = s.release(Math.max(20, (int) Math.ceil(occupancy * 0.05)));
            if (leaving > 0) change.put(s.getId(), -leaving);
        }
    }
}
