package com.mumbai.evacuation.disaster;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HazardOverlayTest {

    /** Straight east-west road 0 -- 1 -- 2 with ~1 km links, plus one long 2 km link 3 -> 4 north of it. */
    private static Graph lineGraph() {
        Graph g = new Graph();
        g.addNode(new Node(0, 19.000, 72.800));
        g.addNode(new Node(1, 19.000, 72.810));
        g.addNode(new Node(2, 19.000, 72.820));
        g.addNode(new Node(3, 19.010, 72.800));
        g.addNode(new Node(4, 19.010, 72.820));
        g.addEdge(new Edge(1, 0, 1, 1050, "primary", 50, 300));
        g.addEdge(new Edge(2, 1, 0, 1050, "primary", 50, 300));
        g.addEdge(new Edge(3, 1, 2, 1050, "primary", 50, 300));
        g.addEdge(new Edge(4, 2, 1, 1050, "primary", 50, 300));
        g.addEdge(new Edge(5, 3, 4, 2100, "motorway", 80, 500));
        return g;
    }

    private static DisasterEvent flood(double lat, double lon, double radius, boolean block) {
        return new DisasterEvent("d", DisasterType.FLOOD, lat, lon, radius, block, 3.0, "test");
    }

    @Test
    void peopleAtTheEpicentreCanStillLeave() {
        Graph g = lineGraph();
        HazardOverlay overlay = HazardOverlay.build(g, List.of(flood(19.000, 72.810, 300, true)));
        // Node 1 is at the epicentre: outbound edges are egress, inbound ones blocked.
        assertFalse(overlay.isBlocked(g.getEdge(2)));   // 1 -> 0 leads away
        assertEquals(HazardOverlay.EGRESS_PENALTY, overlay.multiplier(g.getEdge(2)));
        assertTrue(overlay.isBlocked(g.getEdge(1)));    // 0 -> 1 leads in

        PathResult out = new AStarEngine().findShortestPath(g, overlay.asEdgeCost(), 1, 0);
        assertTrue(out.isPathFound(), "evacuees inside the zone must be able to get out");
        PathResult through = new AStarEngine().findShortestPath(g, overlay.asEdgeCost(), 0, 2);
        assertFalse(through.isPathFound(), "routes must not pass through a blocked zone");
    }

    @Test
    void longSegmentCrossingTheZoneIsBlockedEvenIfItsEndpointsAreOutside() {
        Graph g = lineGraph();
        // Zone centred on the middle of edge 3->4 but its radius (300 m) is far smaller than half its length.
        HazardOverlay overlay = HazardOverlay.build(g, List.of(flood(19.010, 72.8105, 300, true)));
        assertTrue(overlay.isBlocked(g.getEdge(5)));
    }

    @Test
    void congestionOnlyHazardMultipliesTravelTime() {
        Graph g = lineGraph();
        HazardOverlay overlay = HazardOverlay.build(g, List.of(flood(19.000, 72.815, 200, false)));
        Edge e = g.getEdge(3);
        assertFalse(overlay.isBlocked(e));
        assertEquals(3.0, overlay.multiplier(e));
        assertEquals(e.getFreeFlowSeconds() * 3.0, overlay.asEdgeCost().seconds(e), 1e-9);
    }

    @Test
    void sheltersInsideAZoneOrFloodProneDuringFloodsAreUnsafe() {
        Graph g = lineGraph();
        HazardOverlay overlay = HazardOverlay.build(g, List.of(flood(19.000, 72.810, 500, true)));
        assertTrue(overlay.isShelterUnsafe(new Shelter(1, "inside", 19.000, 72.811, 1, 100, false)));
        assertFalse(overlay.isShelterUnsafe(new Shelter(2, "far", 19.050, 72.900, 1, 100, false)));
        assertTrue(overlay.isShelterUnsafe(new Shelter(3, "low-lying", 19.050, 72.900, 1, 100, true)));
    }
}
