package com.mumbai.evacuation.dto;

import com.mumbai.evacuation.model.EvacuationStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Transfer Object containing side-by-side benchmark comparison results
 * between Strategy 1 (Naive Nearest Baseline) and Strategy 2 (Capacity-Aware Engine).
 */
public class EvacuationBenchmarkResult {

    public static class StrategyMetrics {
        public EvacuationStrategy strategy;
        public int totalEvacuees;
        public int evacueesSuccessfullyHoused;
        public int overflowEvacuees;
        public double avgEvacuationTimeMinutes;
        public double maxEvacuationTimeMinutes;
        public double avgTravelDistanceKm;
        public double totalTravelDistanceKm;
        public double shelterUtilizationPercent;
        public double averageRoadCongestionFactor;
        public long executionTimeMs;
        public Map<String, Integer> shelterOccupancies = new LinkedHashMap<>();
    }

    private String scenarioName;
    private StrategyMetrics naiveStrategyMetrics;
    private StrategyMetrics capacityAwareStrategyMetrics;

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public StrategyMetrics getNaiveStrategyMetrics() {
        return naiveStrategyMetrics;
    }

    public void setNaiveStrategyMetrics(StrategyMetrics naiveStrategyMetrics) {
        this.naiveStrategyMetrics = naiveStrategyMetrics;
    }

    public StrategyMetrics getCapacityAwareStrategyMetrics() {
        return capacityAwareStrategyMetrics;
    }

    public void setCapacityAwareStrategyMetrics(StrategyMetrics capacityAwareStrategyMetrics) {
        this.capacityAwareStrategyMetrics = capacityAwareStrategyMetrics;
    }
}
