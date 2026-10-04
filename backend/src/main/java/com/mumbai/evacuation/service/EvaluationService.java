package com.mumbai.evacuation.service;

import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.StrategyMetrics;
import com.mumbai.evacuation.dto.EvaluationDtos.*;
import com.mumbai.evacuation.dto.SimulationParams;
import com.mumbai.evacuation.dto.SimulationRequest;
import com.mumbai.evacuation.model.EvacuationStrategy;
import com.mumbai.evacuation.model.EvacueeGroup;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * Statistical evaluation of the two strategies.
 *
 * Single simulation runs can be misleading, so every evaluation repeats the
 * scenario many times with randomised inputs: each group's size is scaled by a
 * uniform factor in [1 - v, 1 + v] and its location moved up to J metres in a
 * random direction. Each randomised scenario is run with BOTH strategies, so the
 * comparison is paired. Results report mean, standard deviation, min/max and a
 * 95% confidence interval (normal approximation, 1.96·s/√n).
 *
 * Runs are independent and execute in parallel. Fixed seeds make every result
 * reproducible.
 */
@Service
public class EvaluationService {

    static final int MAX_TOTAL_RUNS = 200;
    static final double DEFAULT_SIZE_VARIATION = 0.3;
    static final double DEFAULT_JITTER_METERS = 500;
    static final long DEFAULT_SEED = 42;

    /** Metrics summarised in every evaluation. */
    static final Map<String, ToDoubleFunction<StrategyMetrics>> METRICS = new LinkedHashMap<>();
    static {
        METRICS.put("housedPercent", StrategyMetrics::housedPercent);
        METRICS.put("avgEvacuationTimeMinutes", StrategyMetrics::avgEvacuationTimeMinutes);
        METRICS.put("maxEvacuationTimeMinutes", StrategyMetrics::maxEvacuationTimeMinutes);
        METRICS.put("overflowEvacuees", StrategyMetrics::overflowEvacuees);
        METRICS.put("sheltersOverCapacity", StrategyMetrics::sheltersOverCapacity);
        METRICS.put("congestedRoadKm", StrategyMetrics::congestedRoadKm);
        METRICS.put("crowdedRailKm", StrategyMetrics::crowdedRailKm);
    }

    private final EvacuationEngine engine;

    public EvaluationService(EvacuationEngine engine) {
        this.engine = engine;
    }

    public MonteCarloResult monteCarlo(MonteCarloRequest req) {
        long start = System.currentTimeMillis();
        EvacuationEngine.Scenario scenario = engine.preset(req.scenarioId());
        int runs = req.runs() == null ? 30 : req.runs();
        long seed = req.seed() == null ? DEFAULT_SEED : req.seed();
        double variation = req.sizeVariation() == null ? DEFAULT_SIZE_VARIATION : req.sizeVariation();
        double jitter = req.locationJitterMeters() == null ? DEFAULT_JITTER_METERS : req.locationJitterMeters();
        SimulationParams params = new SimulationRequest(null, null, null, req.personsPerVehicle(),
                req.evacuationWindowHours(), req.capacityScale(), req.walkShare(), req.transitShare())
                .paramsOver(engine.getDefaults());

        List<StrategyMetrics[]> results = runMany(scenario, params, runs, seed, variation, jitter);

        List<RunRow> rows = new ArrayList<>();
        int wins = 0;
        for (int i = 0; i < results.size(); i++) {
            StrategyMetrics naive = results.get(i)[0], aware = results.get(i)[1];
            rows.add(row(i + 1, naive));
            rows.add(row(i + 1, aware));
            if (aware.housedPercent() >= naive.housedPercent()) wins++;
        }
        return new MonteCarloResult(scenario.id(), scenario.name(), runs, seed, variation, jitter, params,
                summarize(results), round(100.0 * wins / runs), rows, System.currentTimeMillis() - start);
    }

    public SensitivityResult sensitivity(SensitivityRequest req) {
        long start = System.currentTimeMillis();
        EvacuationEngine.Scenario scenario = engine.preset(req.scenarioId());
        int runsPerValue = req.runsPerValue() == null ? 10 : req.runsPerValue();
        if ((long) runsPerValue * req.values().size() > MAX_TOTAL_RUNS) {
            throw new IllegalArgumentException("At most " + MAX_TOTAL_RUNS + " runs per sensitivity analysis (values × runsPerValue)");
        }
        long seed = req.seed() == null ? DEFAULT_SEED : req.seed();
        double variation = req.sizeVariation() == null ? DEFAULT_SIZE_VARIATION : req.sizeVariation();
        double jitter = req.locationJitterMeters() == null ? DEFAULT_JITTER_METERS : req.locationJitterMeters();
        SimulationParams base = engine.getDefaults();

        List<SensitivityPoint> points = new ArrayList<>();
        for (double value : req.values().stream().sorted().toList()) {
            SimulationParams p = apply(base, req.parameter(), value);
            // Same seed for every value: differences come from the parameter, not from different random draws.
            points.add(new SensitivityPoint(value, summarize(runMany(scenario, p, runsPerValue, seed, variation, jitter))));
        }
        return new SensitivityResult(scenario.id(), scenario.name(), req.parameter(), runsPerValue, seed, base, points,
                System.currentTimeMillis() - start);
    }

    static SimulationParams apply(SimulationParams base, Parameter parameter, double value) {
        return switch (parameter) {
            case PERSONS_PER_VEHICLE -> base.withPersonsPerVehicle(value);
            case WINDOW_HOURS -> base.withWindowHours(value);
            case CAPACITY_SCALE -> base.withCapacityScale(value);
            case WALK_SHARE -> base.withWalkShare(value);
            case TRANSIT_SHARE -> base.withTransitShare(value);
        };
    }

    /** Runs {@code runs} randomised copies of the scenario; returns [naive, capacityAware] per run, in run order. */
    private List<StrategyMetrics[]> runMany(EvacuationEngine.Scenario scenario, SimulationParams params, int runs,
                                            long seed, double variation, double jitter) {
        HazardOverlay overlay = engine.overlayFor(scenario);
        return IntStream.range(0, runs).parallel()
                .mapToObj(i -> {
                    EvacuationEngine.Scenario randomised = randomise(scenario, new Random(seed + i), variation, jitter);
                    return new StrategyMetrics[]{
                            engine.simulate(randomised, overlay, EvacuationStrategy.NAIVE_NEAREST, params),
                            engine.simulate(randomised, overlay, EvacuationStrategy.CAPACITY_AWARE, params)};
                })
                .toList();
    }

    static EvacuationEngine.Scenario randomise(EvacuationEngine.Scenario s, Random random, double variation, double jitterMeters) {
        List<EvacueeGroup> groups = new ArrayList<>();
        for (EvacueeGroup g : s.groups()) {
            int count = (int) Math.max(1, Math.round(g.count() * (1 - variation + 2 * variation * random.nextDouble())));
            double distance = jitterMeters * Math.sqrt(random.nextDouble()); // uniform over the disc
            double bearing = 2 * Math.PI * random.nextDouble();
            double lat = g.latitude() + distance * Math.cos(bearing) / 111_320.0;
            double lon = g.longitude() + distance * Math.sin(bearing) / (111_320.0 * Math.cos(Math.toRadians(g.latitude())));
            groups.add(new EvacueeGroup(g.id(), g.name(), lat, lon, count, g.wardName()));
        }
        return new EvacuationEngine.Scenario(s.id(), s.name(), s.description(), s.disasters(), groups);
    }

    private static Map<String, Map<String, Stat>> summarize(List<StrategyMetrics[]> results) {
        Map<String, Map<String, Stat>> out = new LinkedHashMap<>();
        String[] names = {"NAIVE_NEAREST", "CAPACITY_AWARE"};
        for (int s = 0; s < 2; s++) {
            Map<String, Stat> metrics = new LinkedHashMap<>();
            for (Map.Entry<String, ToDoubleFunction<StrategyMetrics>> m : METRICS.entrySet()) {
                final int idx = s;
                metrics.put(m.getKey(), stat(results.stream().mapToDouble(r -> m.getValue().applyAsDouble(r[idx])).toArray()));
            }
            out.put(names[s], metrics);
        }
        return out;
    }

    static Stat stat(double[] values) {
        int n = values.length;
        double mean = Arrays.stream(values).average().orElse(0);
        double var = n > 1 ? Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / (n - 1) : 0;
        double std = Math.sqrt(var);
        return new Stat(round(mean), round(std), round(Arrays.stream(values).min().orElse(0)),
                round(Arrays.stream(values).max().orElse(0)), round(n > 1 ? 1.96 * std / Math.sqrt(n) : 0), n);
    }

    private static RunRow row(int run, StrategyMetrics m) {
        return new RunRow(run, m.strategy().name(), m.housedPercent(), m.avgEvacuationTimeMinutes(),
                m.maxEvacuationTimeMinutes(), m.overflowEvacuees(), m.congestedRoadKm(), m.crowdedRailKm());
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
