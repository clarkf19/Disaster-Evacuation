package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.Comparison;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.StrategyMetrics;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.EvacuationStrategy;
import com.mumbai.evacuation.service.EvacuationEngine;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sandboxed evacuation simulations (Command Centre). Simulations never change
 * live state, so they are open to everyone (but rate limited).
 */
@RestController
@RequestMapping("/api/evacuation")
public class EvacuationController {

    private final EvacuationEngine evacuationEngine;

    public EvacuationController(EvacuationEngine evacuationEngine) {
        this.evacuationEngine = evacuationEngine;
    }

    /** GET /api/evacuation/scenarios — preset scenarios. */
    @GetMapping("/scenarios")
    public List<Map<String, Object>> getScenarios() {
        return evacuationEngine.getPresetScenarios();
    }

    /** POST /api/evacuation/compare — run both strategies on the same scenario. */
    @PostMapping("/compare")
    public Comparison compare(@Valid @RequestBody SimulationRequest request) {
        return evacuationEngine.compare(evacuationEngine.resolve(request));
    }

    /** POST /api/evacuation/simulate?strategy=NAIVE_NEAREST|CAPACITY_AWARE — run a single strategy. */
    @PostMapping("/simulate")
    public StrategyMetrics simulate(@RequestParam(defaultValue = "CAPACITY_AWARE") EvacuationStrategy strategy,
                                    @Valid @RequestBody SimulationRequest request) {
        return evacuationEngine.simulate(evacuationEngine.resolve(request), strategy);
    }
}
