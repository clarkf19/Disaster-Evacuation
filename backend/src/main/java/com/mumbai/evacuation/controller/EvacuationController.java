package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.EvacuationBenchmarkResult;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.StrategyMetrics;
import com.mumbai.evacuation.model.EvacueeGroup;
import com.mumbai.evacuation.model.EvacuationStrategy;
import com.mumbai.evacuation.service.EvacuationEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for capacity-aware disaster evacuation simulation & benchmark comparison.
 */
@RestController
@RequestMapping("/api/evacuation")
@CrossOrigin(origins = "*")
public class EvacuationController {

    @Autowired
    private EvacuationEngine evacuationEngine;

    /** GET /api/evacuation/scenarios — list pre-configured disaster scenarios */
    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, Object>>> getScenarios() {
        return ResponseEntity.ok(evacuationEngine.getPresetScenarios());
    }

    /** POST /api/evacuation/scenarios/{id}/load — load a scenario into active state */
    @PostMapping("/scenarios/{id}/load")
    public ResponseEntity<Map<String, Object>> loadScenario(@PathVariable String id) {
        evacuationEngine.loadPresetScenario(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "SCENARIO_LOADED");
        resp.put("scenarioId", id);
        resp.put("activeGroupsCount", evacuationEngine.getActiveEvacueeGroups().size());
        return ResponseEntity.ok(resp);
    }

    /** GET /api/evacuation/groups — list all active evacuee groups */
    @GetMapping("/groups")
    public ResponseEntity<Collection<EvacueeGroup>> getGroups() {
        return ResponseEntity.ok(evacuationEngine.getActiveEvacueeGroups());
    }

    /** POST /api/evacuation/groups — spawn custom evacuee group */
    @PostMapping("/groups")
    public ResponseEntity<EvacueeGroup> addGroup(@RequestBody Map<String, Object> body) {
        String id = body.containsKey("id") ? body.get("id").toString() : "grp-" + System.currentTimeMillis();
        String name = body.getOrDefault("name", "Evacuee Cluster").toString();
        long sourceNodeId = Long.parseLong(body.get("sourceNodeId").toString());
        int count = Integer.parseInt(body.get("count").toString());
        String wardName = body.getOrDefault("wardName", "Mumbai Region").toString();

        EvacueeGroup group = new EvacueeGroup(id, name, sourceNodeId, count, wardName);
        evacuationEngine.addEvacueeGroup(group);
        return ResponseEntity.ok(group);
    }

    /** POST /api/evacuation/simulate — run simulation with specified strategy */
    @PostMapping("/simulate")
    public ResponseEntity<StrategyMetrics> runSimulation(
            @RequestParam(defaultValue = "CAPACITY_AWARE") String strategy) {
        EvacuationStrategy strat = "NAIVE_NEAREST".equalsIgnoreCase(strategy) ? 
            EvacuationStrategy.NAIVE_NEAREST : EvacuationStrategy.CAPACITY_AWARE;
        StrategyMetrics metrics = evacuationEngine.runSimulation(strat);
        return ResponseEntity.ok(metrics);
    }

    /** POST /api/evacuation/compare — compare Strategy 1 vs Strategy 2 */
    @PostMapping("/compare")
    public ResponseEntity<EvacuationBenchmarkResult> compareStrategies(
            @RequestParam(defaultValue = "Active Evacuation Scenario") String scenarioName) {
        EvacuationBenchmarkResult result = evacuationEngine.compareStrategies(scenarioName);
        return ResponseEntity.ok(result);
    }
}
