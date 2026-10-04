package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.Comparison;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.StrategyMetrics;
import com.mumbai.evacuation.dto.EvaluationDtos.*;
import com.mumbai.evacuation.dto.SimulationParams;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.EvacuationStrategy;
import com.mumbai.evacuation.service.EvacuationEngine;
import com.mumbai.evacuation.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sandboxed evacuation simulations and statistical evaluation (Command Centre).
 * Simulations never change live state, so they are open to everyone (but rate limited).
 */
@RestController
public class EvacuationController {

    private final EvacuationEngine evacuationEngine;
    private final EvaluationService evaluationService;

    public EvacuationController(EvacuationEngine evacuationEngine, EvaluationService evaluationService) {
        this.evacuationEngine = evacuationEngine;
        this.evaluationService = evaluationService;
    }

    /** GET /api/evacuation/scenarios — preset scenarios. */
    @GetMapping("/api/evacuation/scenarios")
    public List<Map<String, Object>> getScenarios() {
        return evacuationEngine.getPresetScenarios();
    }

    /** GET /api/evacuation/defaults — default simulation assumptions. */
    @GetMapping("/api/evacuation/defaults")
    public SimulationParams defaults() {
        return evacuationEngine.getDefaults();
    }

    /** POST /api/evacuation/compare — run both strategies on the same scenario. */
    @PostMapping("/api/evacuation/compare")
    public Comparison compare(@Valid @RequestBody SimulationRequest request) {
        return evacuationEngine.compare(evacuationEngine.resolve(request), request.paramsOver(evacuationEngine.getDefaults()));
    }

    /** POST /api/evacuation/simulate?strategy=NAIVE_NEAREST|CAPACITY_AWARE — run a single strategy. */
    @PostMapping("/api/evacuation/simulate")
    public StrategyMetrics simulate(@RequestParam(defaultValue = "CAPACITY_AWARE") EvacuationStrategy strategy,
                                    @Valid @RequestBody SimulationRequest request) {
        return evacuationEngine.simulate(evacuationEngine.resolve(request), strategy,
                request.paramsOver(evacuationEngine.getDefaults()));
    }

    /** POST /api/evaluation/monte-carlo — repeated randomised runs with summary statistics. */
    @PostMapping("/api/evaluation/monte-carlo")
    public MonteCarloResult monteCarlo(@Valid @RequestBody MonteCarloRequest request) {
        return evaluationService.monteCarlo(request);
    }

    /** POST /api/evaluation/sensitivity — sweep one assumption and summarise each value. */
    @PostMapping("/api/evaluation/sensitivity")
    public SensitivityResult sensitivity(@Valid @RequestBody SensitivityRequest request) {
        return evaluationService.sensitivity(request);
    }
}
