package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A 6 km straight road from node 1 to node 3 through node 2, one-way eastwards,
 * plus a railway with stations next to nodes 1 and 3.
 */
class TravelModeTest {

    private static Graph corridor(double elevation) {
        Graph g = new Graph();
        g.addNode(new Node(1, 19.000, 72.800, elevation, null, false));
        g.addNode(new Node(2, 19.000, 72.830, elevation, null, false));
        g.addNode(new Node(3, 19.000, 72.857, elevation, null, false));
        g.addEdge(new Edge(1, 1, 2, 3150, "primary", 50, 300)); // one-way east
        g.addEdge(new Edge(2, 2, 3, 2850, "primary", 50, 300));
        g.addWalkingReverseEdges();
        g.addRailNetwork(
                List.of(new Node(-1, 19.0005, 72.8002, elevation, "West", true),
                        new Node(-2, 19.0005, 72.8568, elevation, "East", true)),
                List.of(new Graph.RailLink(-1, -2, "Test Line", 6000)));
        return g;
    }

    private static PathResult route(Graph g, TravelMode mode, HazardOverlay overlay, long from, long to) {
        return new AStarEngine().findShortestPath(g, CostModel.of(mode, overlay), from, to, mode.maxSpeedKmH(g));
    }

    @Test
    void walkersMayGoAgainstOneWayStreetsButDriversMayNot() {
        Graph g = corridor(10);
        assertFalse(route(g, TravelMode.DRIVE, HazardOverlay.empty(), 3, 1).isPathFound());
        PathResult walk = route(g, TravelMode.WALK, HazardOverlay.empty(), 3, 1);
        assertTrue(walk.isPathFound());
        // 6 km at 4.5 km/h = 80 minutes
        assertEquals(80, walk.getTotalTravelTimeMinutes(), 0.5);
        assertEquals(2, g.getEdgeCount(Edge.Kind.ROAD_REVERSE));
    }

    @Test
    void transitTakesTheTrainWhenItIsFasterThanWalking() {
        Graph g = corridor(10);
        PathResult transit = route(g, TravelMode.TRANSIT, HazardOverlay.empty(), 1, 3);
        assertTrue(transit.isPathFound());
        assertTrue(transit.getPathEdges().stream().anyMatch(e -> e.getKind() == Edge.Kind.RAIL));
        // wait 5 min + 6 km at 40 km/h (9 min) + dwell + exit + short walks — far less than 80 min walking
        assertTrue(transit.getTotalTravelTimeMinutes() < 20, "took " + transit.getTotalTravelTimeMinutes());
        // ...and is identical to Dijkstra (admissible heuristic)
        PathResult dijkstra = new DijkstraEngine().findShortestPath(g, CostModel.of(TravelMode.TRANSIT, HazardOverlay.empty()), 1, 3);
        assertEquals(dijkstra.getTotalTravelTimeSeconds(), transit.getTotalTravelTimeSeconds(), 1e-6);
    }

    @Test
    void floodedStationsCannotBeUsed() {
        Graph g = corridor(10);
        HazardOverlay flood = HazardOverlay.build(g, List.of(
                new DisasterEvent("f", DisasterType.FLOOD, 19.0005, 72.8568, 300, true, 3, "station flooded")));
        PathResult transit = route(g, TravelMode.TRANSIT, flood, 1, 2);
        assertTrue(transit.isPathFound());
        assertTrue(transit.getPathEdges().stream().noneMatch(e -> e.getKind() == Edge.Kind.RAIL),
                "the train to a flooded station must not be used");
    }

    @Test
    void lowLyingRoadsNearAFloodAreSlowedButHighGroundIsNot() {
        DisasterEvent nearbyFlood = new DisasterEvent("f", DisasterType.FLOOD, 19.010, 72.830, 200, true, 3, "flood 1 km north");
        Graph low = corridor(2.0);
        HazardOverlay lowOverlay = HazardOverlay.build(low, List.of(nearbyFlood));
        assertEquals(HazardOverlay.VERY_LOW_LYING_PENALTY, lowOverlay.multiplier(low.getEdge(1)));
        assertTrue(lowOverlay.getLowLyingEdgeCount() > 0);

        Graph high = corridor(25.0);
        assertEquals(1.0, HazardOverlay.build(high, List.of(nearbyFlood)).multiplier(high.getEdge(1)));

        // A fire does not cause water-logging.
        DisasterEvent fire = new DisasterEvent("x", DisasterType.FIRE, 19.010, 72.830, 200, true, 3, "fire");
        assertEquals(1.0, HazardOverlay.build(low, List.of(fire)).multiplier(low.getEdge(1)));
    }
}
