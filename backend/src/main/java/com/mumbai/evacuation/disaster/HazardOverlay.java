package com.mumbai.evacuation.disaster;

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
 *   <li>Road-blocking disaster, edge starts inside the zone and leads away from
 *       the epicentre: <b>egress</b> — passable with a slow-down penalty, so
 *       people inside a hazard zone can always get out.</li>
 *   <li>Road-blocking disaster, any other edge touching the zone: blocked.</li>
 *   <li>Congestion-only disaster: travel time multiplied by the disaster's
 *       congestion multiplier.</li>
 * </ul>
 * When several disasters overlap, an edge is blocked if any of them blocks it,
 * and the largest multiplier wins.
 */
public final class HazardOverlay {

    /** Travel-time penalty for leaving a road-blocking hazard zone (debris, water, panic). */
    public static final double EGRESS_PENALTY = 2.0;

    private static final HazardOverlay EMPTY = new HazardOverlay(List.of(), Set.of(), Map.of());

    private final List<DisasterEvent> disasters;
    private final Set<Long> blockedEdgeIds;
    private final Map<Long, Double> multipliers;

    private HazardOverlay(List<DisasterEvent> disasters, Set<Long> blockedEdgeIds, Map<Long, Double> multipliers) {
        this.disasters = disasters;
        this.blockedEdgeIds = blockedEdgeIds;
        this.multipliers = multipliers;
    }

    public static HazardOverlay empty() { return EMPTY; }

    public static HazardOverlay build(Graph graph, Collection<DisasterEvent> disasters) {
        if (disasters.isEmpty()) return EMPTY;
        Set<Long> blocked = new HashSet<>();
        Map<Long, Double> multipliers = new HashMap<>();

        for (Edge edge : graph.getAllEdges()) {
            Node a = graph.getNode(edge.getSourceNodeId());
            Node b = graph.getNode(edge.getTargetNodeId());
            if (a == null || b == null) continue;

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
                    boolean egress = distA <= radius && distB > distA;
                    if (egress) {
                        multipliers.merge(edge.getId(), EGRESS_PENALTY, Math::max);
                    } else {
                        blocked.add(edge.getId());
                    }
                } else {
                    multipliers.merge(edge.getId(), d.getCongestionMultiplier(), Math::max);
                }
            }
        }
        return new HazardOverlay(List.copyOf(disasters), Set.copyOf(blocked), Map.copyOf(multipliers));
    }

    public List<DisasterEvent> getDisasters() { return disasters; }
    public boolean isEmpty() { return disasters.isEmpty(); }
    public boolean isBlocked(Edge edge) { return blockedEdgeIds.contains(edge.getId()); }
    public double multiplier(Edge edge) { return multipliers.getOrDefault(edge.getId(), 1.0); }
    public int getBlockedEdgeCount() { return blockedEdgeIds.size(); }

    /** Cost function: free-flow time scaled by hazard multipliers; blocked edges are impassable. */
    public EdgeCost asEdgeCost() {
        return edge -> isBlocked(edge) ? Double.POSITIVE_INFINITY : edge.getFreeFlowSeconds() * multiplier(edge);
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
