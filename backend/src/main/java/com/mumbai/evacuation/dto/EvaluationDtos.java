package com.mumbai.evacuation.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/** Requests and results for repeated-run evaluation and sensitivity analysis. */
public final class EvaluationDtos {

    private EvaluationDtos() {}

    /** Parameters that a sensitivity sweep can vary. */
    public enum Parameter { PERSONS_PER_VEHICLE, WINDOW_HOURS, CAPACITY_SCALE, WALK_SHARE, TRANSIT_SHARE }

    /**
     * Monte Carlo evaluation: run a preset {@code runs} times with randomised group
     * sizes (± {@code sizeVariation}) and locations (within {@code locationJitterMeters}).
     */
    public record MonteCarloRequest(
            @NotBlank @Size(max = 64) String scenarioId,
            @Min(2) @Max(100) Integer runs,
            Long seed,
            @DecimalMin("0") @DecimalMax("0.9") Double sizeVariation,
            @DecimalMin("0") @DecimalMax("3000") Double locationJitterMeters,
            @DecimalMin("1") @DecimalMax("60") Double personsPerVehicle,
            @DecimalMin("0.25") @DecimalMax("24") Double evacuationWindowHours,
            @DecimalMin("0.1") @DecimalMax("5") Double capacityScale,
            @DecimalMin("0") @DecimalMax("1") Double walkShare,
            @DecimalMin("0") @DecimalMax("1") Double transitShare) {}

    /** Sensitivity sweep of one parameter; every value is evaluated with {@code runsPerValue} randomised runs. */
    public record SensitivityRequest(
            @NotBlank @Size(max = 64) String scenarioId,
            @NotNull Parameter parameter,
            @NotEmpty @Size(max = 8) List<@NotNull Double> values,
            @Min(1) @Max(30) Integer runsPerValue,
            Long seed,
            @DecimalMin("0") @DecimalMax("0.9") Double sizeVariation,
            @DecimalMin("0") @DecimalMax("3000") Double locationJitterMeters) {}

    /** Summary statistics of one metric over many runs (ci95 = half-width of the 95% confidence interval of the mean). */
    public record Stat(double mean, double std, double min, double max, double ci95, int n) {}

    /** metrics: strategy name -> metric name -> statistics. */
    public record MonteCarloResult(String scenarioId, String scenarioName, int runs, long seed, double sizeVariation,
                                   double locationJitterMeters, SimulationParams params,
                                   Map<String, Map<String, Stat>> metrics,
                                   double capacityAwareWinRate, List<RunRow> perRun, long executionTimeMs) {}

    /** One strategy's headline numbers in one run (for CSV export and plots). */
    public record RunRow(int run, String strategy, double housedPercent, double avgMinutes, double maxMinutes,
                         int overflow, double congestedRoadKm, double crowdedRailKm) {}

    public record SensitivityPoint(double value, Map<String, Map<String, Stat>> metrics) {}

    public record SensitivityResult(String scenarioId, String scenarioName, Parameter parameter, int runsPerValue,
                                    long seed, SimulationParams baseParams, List<SensitivityPoint> points,
                                    long executionTimeMs) {}
}
