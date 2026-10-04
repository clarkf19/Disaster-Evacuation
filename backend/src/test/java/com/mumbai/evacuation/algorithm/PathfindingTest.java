package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Small hand-built graph (roughly 1 km grid):
 *
 *   1 --- 2 --- 3
 *   |           |
 *   4 --------- 5
 *
 * Top row is a fast road, bottom row a slow one.
 */
class PathfindingTest {

    static Graph sampleGraph() {
        Graph g = new Graph();
        g.addNode(new Node(1, 19.000, 72.800));
        g.addNode(new Node(2, 19.000, 72.810));
        g.addNode(new Node(3, 19.000, 72.820));
        g.addNode(new Node(4, 18.990, 72.800));
        g.addNode(new Node(5, 18.990, 72.820));
        long id = 1;
        id = both(g, id, 1, 2, 1050, 60);
        id = both(g, id, 2, 3, 1050, 60);
        id = both(g, id, 1, 4, 1110, 30);
        id = both(g, id, 4, 5, 2100, 30);
        both(g, id, 3, 5, 1110, 30);
        return g;
    }

    private static long both(Graph g, long id, long a, long b, double meters, double kmh) {
        g.addEdge(new Edge(id++, a, b, meters, "primary", kmh, 300));
        g.addEdge(new Edge(id++, b, a, meters, "primary", kmh, 300));
        return id;
    }

    @Test
    void dijkstraAndAStarFindTheSameOptimalPath() {
        Graph g = sampleGraph();
        PathResult d = new DijkstraEngine().findShortestPath(g, EdgeCost.FREE_FLOW, 4, 3);
        PathResult a = new AStarEngine().findShortestPath(g, EdgeCost.FREE_FLOW, 4, 3);
        assertTrue(d.isPathFound());
        assertTrue(a.isPathFound());
        assertEquals(d.getTotalTravelTimeSeconds(), a.getTotalTravelTimeSeconds(), 1e-9);
        // 4 -> 1 -> 2 -> 3 via the fast top road beats 4 -> 5 -> 3
        assertEquals(java.util.List.of(4L, 1L, 2L, 3L), a.getPathNodes().stream().map(Node::getId).toList());
    }

    @Test
    void impassableEdgesForceADetour() {
        Graph g = sampleGraph();
        EdgeCost blockTop = e -> (e.getSourceNodeId() == 1 && e.getTargetNodeId() == 2)
                ? Double.POSITIVE_INFINITY : e.getFreeFlowSeconds();
        PathResult a = new AStarEngine().findShortestPath(g, blockTop, 4, 3);
        assertTrue(a.isPathFound());
        assertEquals(java.util.List.of(4L, 5L, 3L), a.getPathNodes().stream().map(Node::getId).toList());
    }

    @Test
    void unreachableTargetReturnsNoPath() {
        Graph g = sampleGraph();
        PathResult a = new AStarEngine().findShortestPath(g, e -> Double.POSITIVE_INFINITY, 1, 3);
        assertFalse(a.isPathFound());
        assertTrue(a.getPathNodes().isEmpty());
    }

    @Test
    void sourceEqualsTargetIsAZeroLengthPath() {
        PathResult a = new AStarEngine().findShortestPath(sampleGraph(), EdgeCost.FREE_FLOW, 2, 2);
        assertTrue(a.isPathFound());
        assertEquals(0.0, a.getTotalTravelTimeSeconds());
        assertEquals(1, a.getPathNodes().size());
    }

    @Test
    void multiTargetSearchMatchesIndividualSearches() {
        Graph g = sampleGraph();
        ShortestPathTree tree = new DijkstraEngine().searchToTargets(g, EdgeCost.FREE_FLOW, 1, Set.of(3L, 5L));
        for (long target : new long[]{3, 5}) {
            PathResult single = new AStarEngine().findShortestPath(g, EdgeCost.FREE_FLOW, 1, target);
            assertEquals(single.getTotalTravelTimeSeconds(), tree.costTo(target), 1e-9);
            assertEquals(single.getTotalTravelTimeSeconds(), tree.pathTo(target).getTotalTravelTimeSeconds(), 1e-9);
        }
    }

    @Test
    void nearestNodeUsesTrueDistance() {
        Graph g = sampleGraph();
        assertEquals(2, g.findNearestNode(19.0004, 72.8102).getId());
        assertEquals(5, g.findNearestNode(18.95, 72.90).getId()); // far away still resolves
    }
}
