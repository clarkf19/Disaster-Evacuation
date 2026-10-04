package com.mumbai.evacuation;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.algorithm.EdgeCost;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.*;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.model.PathResult;
import com.mumbai.evacuation.model.Shelter;
import com.mumbai.evacuation.service.EvacuationEngine;
import com.mumbai.evacuation.service.GraphService;
import com.mumbai.evacuation.service.ShelterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the real Mumbai road graph and shelter data. */
@SpringBootTest(properties = {"tomtom.api.key=", "llm.api-key=", "ratelimit.enabled=false"})
class EvacuationEngineTest {

    @Autowired EvacuationEngine engine;
    @Autowired GraphService graphService;
    @Autowired ShelterService shelterService;

    @Test
    void realGraphLoadsFromClasspath() {
        assertTrue(graphService.getGraph().getNodeCount() > 8000);
        assertTrue(graphService.getGraph().getEdgeCount() > 17000);
        shelterService.getAllShelters().forEach(s -> assertTrue(s.getNearestNodeId() >= 0, s.getName()));
    }

    @Test
    void aStarMatchesDijkstraOnRandomRealPairs() {
        Graph graph = graphService.getGraph();
        List<Node> nodes = new ArrayList<>(graph.getAllNodes());
        nodes.sort(Comparator.comparingLong(Node::getId));
        Random random = new Random(42);
        AStarEngine aStar = new AStarEngine();
        DijkstraEngine dijkstra = new DijkstraEngine();
        for (int i = 0; i < 40; i++) {
            long src = nodes.get(random.nextInt(nodes.size())).getId();
            long dst = nodes.get(random.nextInt(nodes.size())).getId();
            PathResult a = aStar.findShortestPath(graph, EdgeCost.FREE_FLOW, src, dst);
            PathResult d = dijkstra.findShortestPath(graph, EdgeCost.FREE_FLOW, src, dst);
            assertEquals(d.isPathFound(), a.isPathFound());
            if (d.isPathFound()) {
                assertEquals(d.getTotalTravelTimeSeconds(), a.getTotalTravelTimeSeconds(), 1e-6, src + "->" + dst);
                assertTrue(a.getNodesExplored() <= d.getNodesExplored());
            }
        }
    }

    @Test
    void everyPresetIsConsistentAndRespectsCapacity() {
        for (Map<String, Object> preset : engine.getPresetScenarios()) {
            String id = (String) preset.get("id");
            Comparison result = engine.compare(engine.resolve(new SimulationRequest(id, null, null)));
            for (StrategyMetrics m : List.of(result.naive(), result.capacityAware())) {
                assertEquals(m.totalEvacuees(), m.evacueesHoused() + m.overflowEvacuees(), id + " " + m.strategy());
                for (ShelterLoad load : m.shelters()) {
                    assertTrue(load.housed() <= load.capacity(), id + " " + m.strategy() + " " + load.name() + " over capacity");
                    if (load.unsafe()) assertEquals(0, load.arrivals(), id + ": unsafe shelter used " + load.name());
                }
            }
            for (ShelterLoad load : result.capacityAware().shelters()) {
                assertTrue(load.arrivals() <= load.capacity(), id + ": capacity-aware sent too many to " + load.name());
            }
        }
    }

    @Test
    void groupsAtTheDisasterEpicentreAreEvacuated() {
        // Before the fix every road out of a blocking zone was blocked, so these groups were stranded.
        for (String id : List.of("sion_flood", "bkc_fire", "dadar_bridge")) {
            Comparison result = engine.compare(engine.resolve(new SimulationRequest(id, null, null)));
            GroupOutcome epicentre = result.capacityAware().groups().stream()
                    .filter(g -> g.id().equals("grp-1")).findFirst().orElseThrow();
            assertNotEquals("UNREACHABLE", epicentre.status(), id);
            assertTrue(epicentre.housed() > 0, id);
        }
    }

    @Test
    void naiveStrategyReportsOverflowHonestlyAndCapacityAwareDoesBetter() {
        Comparison result = engine.compare(engine.resolve(new SimulationRequest("western_suburbs_flood", null, null)));
        assertTrue(result.naive().overflowEvacuees() > 0, "stress test should overflow the naive strategy");
        assertTrue(result.naive().sheltersOverCapacity() > 0);
        assertTrue(result.capacityAware().evacueesHoused() > result.naive().evacueesHoused());
    }

    @Test
    void simulationsDoNotTouchLiveState() {
        Map<Long, Integer> before = new HashMap<>();
        shelterService.getAllShelters().forEach(s -> before.put(s.getId(), s.getCurrentOccupancy()));
        int disastersBefore = graphService.getActiveDisasters().size();

        engine.compare(engine.resolve(new SimulationRequest("bkc_fire", null, null)));

        assertEquals(disastersBefore, graphService.getActiveDisasters().size());
        for (Shelter s : shelterService.getAllShelters()) {
            assertEquals(before.get(s.getId()), s.getCurrentOccupancy());
        }
    }

    @Test
    void largeGroupsAreSplitAcrossShelters() {
        SimulationRequest request = new SimulationRequest(null, List.of(), List.of(
                new SimulationRequest.Group("big", "Huge crowd", 19.0178, 72.8478, 60_000, "G-North")));
        StrategyMetrics m = engine.simulate(engine.resolve(request), com.mumbai.evacuation.model.EvacuationStrategy.CAPACITY_AWARE);
        long sheltersUsed = m.allocations().stream().map(Allocation::shelterId).distinct().count();
        assertTrue(sheltersUsed > 1, "60k people cannot fit one shelter and must be split");
        assertEquals(60_000, m.evacueesHoused());
    }
}
