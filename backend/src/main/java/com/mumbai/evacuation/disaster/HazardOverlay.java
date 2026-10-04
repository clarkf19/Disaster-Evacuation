package com.mumbai.evacuation.disaster;

import com.mumbai.evacuation.algorithm.CostModel;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.model.*;

import java.util.*;

/**
 * Immutable snapshot of how a set of disasters affects the road network.
 *
 * Rules per edge and disaster (the whole road segment is tested, not just its
 * midpoint, so long segments crossing a zone are caught):
 * <ul>
 *   <li>Segment does not touch the zone: unaffected.</li>
 *   <li>Road-blocking disaster, edge starts inside the zone: <b>egress</b> —
 *       passable with a penalty (×{@link #EGRESS_PENALTY} moving away from the
 *       centre, ×{@link #INWARD_PENALTY} moving towards it), so people inside a
 *       hazard zone can always get out along real, curving streets.</li>
 *   <li>Road-blocking disaster, edge starts outside but touches the zone:
 *       blocked — no route can enter the zone.</li>
 *   <li>Congestion-only disaster: travel time multiplied by the disaster's
 *       congestion multiplier.</li>
 * </ul>
 * When several disasters overlap, an edge is blocked if any of them blocks it,
 * and the largest multiplier wins.
 *
 * Floods also affect the surrounding low-lying ground: within
 * {@link #FLOOD_INFLUENCE_FACTOR} × the zone radius (at least 2 km), road and rail
 * edges whose average elevation is at or below {@link #LOW_LYING_M} are slowed
 * (water-logging), more so below {@link #VERY_LOW_LYING_M}.
 */
public final class HazardOverlay {

    /** Travel-time penalty for leaving a road-blocking hazard zone (debris, water, panic). */
    public static final double EGRESS_PENALTY = 2.0;
    /** Penalty for moving towards the centre while still inside a road-blocking zone. */
    public static final double INWARD_PENALTY = 4.0;
    /** Elevation thresholds (m, Copernicus surface model) for water-logging penalties near floods. */
    public static final double LOW_LYING_M = 6.0;
    public static final double VERY_LOW_LYING_M = 3.0;
    public static final double LOW_LYING_PENALTY = 1.3;
    public static final double VERY_LOW_LYING_PENALTY = 1.6;
    static final double FLOOD_INFLUENCE_FACTOR = 3.0;
    static final double MIN_FLOOD_INFLUENCE_M = 2_000;

    private static final HazardOverlay EMPTY = new HazardOverlay(List.of(), Set.of(), Set.of(), Map.of(), 0);

    private final List<DisasterEvent> disasters;
    private final Set<Long> blockedEdgeIds;
    private final Set<Long> egressEdgeIds;
    private final Map<Long, Double> multipliers;
    private final int lowLyingEdgeCount;

    private HazardOverlay(List<DisasterEvent> disasters, Set<Long> blockedEdgeIds, Set<Long> egressEdgeIds,
                          Map<Long, Double> multipliers, int lowLyingEdgeCount) {
        this.disasters = disasters;
        this.blockedEdgeIds = blockedEdgeIds;
        this.egressEdgeIds = egressEdgeIds;
        this.multipliers = multipliers;
        this.lowLyingEdgeCount = lowLyingEdgeCount;
    }

    public static HazardOverlay empty() { return EMPTY; }

    public static HazardOverlay build(Graph graph, Collection<DisasterEvent> disasters) {
        if (disasters.isEmpty()) return EMPTY;
        Set<Long> blocked = new HashSet<>();
        Set<Long> egressEdges = new HashSet<>();
        Map<Long, Double> multipliers = new HashMap<>();
        List<DisasterEvent> floods = disasters.stream().filter(d -> d.getType() == DisasterType.FLOOD).toList();
        int lowLying = 0;

        for (Edge edge : graph.getAllEdges()) {
            Node a = graph.getNode(edge.getSourceNodeId());
            Node b = graph.getNode(edge.getTargetNodeId());
            if (a == null || b == null) continue;

            double penalty = lowLyingPenalty(edge, a, b, floods);
            if (penalty > 1.0) {
                multipliers.merge(edge.getId(), penalty, Math::max);
                lowLying++;
            }

            for (DisasterEvent d : disasters) {
                double cLat = d.getCenterLatitude(), cLon = d.getCenterLongitude();
                double radius = d.getAffectedRadiusMeters();
                double distA = GeoUtils.haversineMeters(cLat, cLon, a.getLatitude(), a.getLongitude());
                // Cheap reject: segment can't reach the zone if its start is farther than radius + length.
                if (distA > radius + edge.getDistanceMeters() + 50) continue;
                double segDist = GeoUtils.pointToSegmentMeters(cLat, cLon,
                        a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
                if (segDist > radius) continue;

                if (d.isBlockRoads()) {
                    double distB = GeoUtils.haversineMeters(cLat, cLon, b.getLatitude(), b.getLongitude());
                    // Inside the zone every road may be used to get out (real streets curve, so the
                    // way out sometimes bends inwards first), but moving inwards is penalised more.
                    // Edges that start outside and touch the zone stay blocked, so no route can enter.
                    if (distA <= radius) {
                        double exitPenalty = distB > distA ? EGRESS_PENALTY : INWARD_PENALTY;
                        multipliers.merge(edge.getId(), exitPenalty, Math::max);
                        egressEdges.add(edge.getId());
                    } else {
                        blocked.add(edge.getId());
                    }
                } else {
                    multipliers.merge(edge.getId(), d.getCongestionMultiplier(), Math::max);
                }
            }
        }
        egressEdges.removeAll(blocked); // blocked by another overlapping zone
        return new HazardOverlay(List.copyOf(disasters), Set.copyOf(blocked), Set.copyOf(egressEdges),
                Map.copyOf(multipliers), lowLying);
    }

    /** Water-logging penalty for low-lying road/rail edges near an active flood (1.0 = none). */
    static double lowLyingPenalty(Edge edge, Node a, Node b, List<DisasterEvent> floods) {
        if (floods.isEmpty() || edge.getKind() == Edge.Kind.TRANSFER || !a.hasElevation() || !b.hasElevation()) return 1.0;
        double elevation = (a.getElevationM() + b.getElevationM()) / 2;
        if (elevation > LOW_LYING_M) return 1.0;
        for (DisasterEvent f : floods) {
            double influence = Math.max(MIN_FLOOD_INFLUENCE_M, FLOOD_INFLUENCE_FACTOR * f.getAffectedRadiusMeters());
            double d = GeoUtils.pointToSegmentMeters(f.getCenterLatitude(), f.getCenterLongitude(),
                    a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
            if (d <= influence) {
                return elevation <= VERY_LOW_LYING_M ? VERY_LOW_LYING_PENALTY : LOW_LYING_PENALTY;
            }
        }
        return 1.0;
    }

    public List<DisasterEvent> getDisasters() { return disasters; }
    public boolean isEmpty() { return disasters.isEmpty(); }
    public boolean isBlocked(Edge edge) { return blockedEdgeIds.contains(edge.getId()); }
    /** Edge leads out of a road-blocking zone (passable, with a penalty). */
    public boolean isEgress(Edge edge) { return egressEdgeIds.contains(edge.getId()); }
    public double multiplier(Edge edge) { return multipliers.getOrDefault(edge.getId(), 1.0); }
    public int getBlockedEdgeCount() { return blockedEdgeIds.size(); }
    /** Number of edges slowed because they are low-lying near a flood. */
    public int getLowLyingEdgeCount() { return lowLyingEdgeCount; }

    /** Driving cost function: free-flow time × hazard multipliers; blocked or non-road edges are impassable. */
    public EdgeCost asEdgeCost() {
        return CostModel.of(TravelMode.DRIVE, this);
    }

    /** Disasters whose zone contains the given point. */
    public List<DisasterEvent> disastersAt(double lat, double lon) {
        List<DisasterEvent> hits = new ArrayList<>();
        for (DisasterEvent d : disasters) {
            if (GeoUtils.haversineMeters(lat, lon, d.getCenterLatitude(), d.getCenterLongitude()) <= d.getAffectedRadiusMeters()) {
                hits.add(d);
            }
        }
        return hits;
    }

    /**
     * A shelter is unsafe if it lies inside any hazard zone, or if it is a
     * flood-prone site while a flood is active anywhere in the city.
     */
    public boolean isShelterUnsafe(Shelter shelter) {
        if (!disastersAt(shelter.getLatitude(), shelter.getLongitude()).isEmpty()) return true;
        return shelter.isFloodProne() && disasters.stream().anyMatch(d -> d.getType() == DisasterType.FLOOD);
    }
}
