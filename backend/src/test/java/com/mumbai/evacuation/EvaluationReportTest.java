package com.mumbai.evacuation;

import com.mumbai.evacuation.dto.EvaluationDtos.*;
import com.mumbai.evacuation.service.EvacuationEngine;
import com.mumbai.evacuation.service.EvaluationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates docs/evaluation-results.md (not part of the normal test run).
 *
 *   ./mvnw test -Dsurefire.excludedGroups= -Dgroups=report
 */
@Tag("report")
@SpringBootTest(properties = {"tomtom.api.key=", "llm.api-key=", "ratelimit.enabled=false", "demo.enabled=false"})
class EvaluationReportTest {

    private static final int RUNS = 30;
    private static final long SEED = 42;

    @Autowired EvaluationService evaluation;
    @Autowired EvacuationEngine engine;

    @Test
    void writeReport() throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# Evaluation results\n\n");
        md.append("Generated ").append(LocalDate.now()).append(" by `EvaluationReportTest` ")
          .append("(`./mvnw test -Dsurefire.excludedGroups= -Dgroups=report`). Every number is reproducible from the seed.\n\n");
        md.append(String.format(Locale.US,
                "**Method.** Each scenario was run %d times (seed %d). In every run each group's size is scaled by a random factor in [0.7, 1.3] "
                + "and its location moved up to 500 m. Both strategies run on the same randomised copy (a paired comparison). "
                + "Values are the mean ± 95%% confidence interval (1.96·s/√n). Default assumptions: %.0f%% walk, %.0f%% train, the rest drive "
                + "(%.0f people per vehicle) over %.0f h.\n\n",
                RUNS, SEED, engine.getDefaults().walkShare() * 100, engine.getDefaults().transitShare() * 100,
                engine.getDefaults().personsPerVehicle(), engine.getDefaults().evacuationWindowHours()));

        md.append("## Monte Carlo comparison\n\n");
        md.append("| Scenario | Evacuees | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Shelters over capacity — naive | Aware ≥ naive |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> preset : engine.getPresetScenarios()) {
            String id = (String) preset.get("id");
            MonteCarloResult r = evaluation.monteCarlo(new MonteCarloRequest(id, RUNS, SEED, 0.3, 500.0, null, null, null, null, null));
            Map<String, Stat> n = r.metrics().get("NAIVE_NEAREST"), a = r.metrics().get("CAPACITY_AWARE");
            md.append(String.format(Locale.US, "| %s | %,d | %s | %s | %s | %s | %s | %.0f%% |\n",
                    r.scenarioName(), (Integer) preset.get("totalEvacuees"),
                    fmt(n.get("housedPercent")), fmt(a.get("housedPercent")),
                    fmt(n.get("avgEvacuationTimeMinutes")), fmt(a.get("avgEvacuationTimeMinutes")),
                    fmt(n.get("sheltersOverCapacity")), r.capacityAwareWinRate()));
        }

        md.append("\n## Sensitivity — western suburbs stress test\n\n");
        md.append("Each value: 10 randomised runs, same seeds for every value (so differences come from the parameter).\n\n");
        sweep(md, Parameter.CAPACITY_SCALE, List.of(0.5, 1.0, 1.5, 2.0));
        sweep(md, Parameter.PERSONS_PER_VEHICLE, List.of(2.0, 4.0, 10.0, 30.0));
        sweep(md, Parameter.WALK_SHARE, List.of(0.0, 0.25, 0.5, 0.75));
        sweep(md, Parameter.TRANSIT_SHARE, List.of(0.0, 0.2, 0.4, 0.6));

        md.append("\n## How to read this\n\n");
        md.append("- **Average travel time only counts people who got a shelter place.** The naive strategy sends every group to the "
                + "single nearest shelter, which is usually a small school, so it houses few people quickly and turns the rest away. "
                + "Capacity-aware assignment houses far more people but sends many of them farther. Compare times only together "
                + "with housed %.\n");
        md.append("- **Crowded rail km is 0 here.** At the assumed emergency capacity (30,000 people/hour per line and direction), "
                + "the train share in these scenarios never saturates a line. Lower `TravelMode.RAIL_CAPACITY_PER_HOUR` to study "
                + "a degraded service.\n");
        md.append("- **People per vehicle doesn't change housed %**, because it only affects road congestion (and therefore travel time).\n");
        md.append("\n## Caveats\n\n");
        md.append("- Shelter capacities are estimates and the shelter list is incomplete (see `shelters.json`). Absolute housed % depends heavily on them.\n");
        md.append("- Travel times come from a static model (speed limits, congestion steps, average train speed), not observed traffic.\n");
        md.append("- The comparison between strategies is the robust part. Absolute numbers should not be read as forecasts.\n");

        Path out = Path.of("..", "docs", "evaluation-results.md");
        Files.writeString(out, md.toString());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    private void sweep(StringBuilder md, Parameter parameter, List<Double> values) {
        SensitivityResult r = evaluation.sensitivity(new SensitivityRequest("western_suburbs_flood", parameter, values, 10, SEED, 0.3, 500.0));
        md.append("### ").append(parameter).append("\n\n");
        md.append("| Value | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Congested road km — aware | Crowded rail km — aware |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (SensitivityPoint p : r.points()) {
            Map<String, Stat> n = p.metrics().get("NAIVE_NEAREST"), a = p.metrics().get("CAPACITY_AWARE");
            md.append(String.format(Locale.US, "| %s | %s | %s | %s | %s | %s | %s |\n", trim(p.value()),
                    fmt(n.get("housedPercent")), fmt(a.get("housedPercent")),
                    fmt(n.get("avgEvacuationTimeMinutes")), fmt(a.get("avgEvacuationTimeMinutes")),
                    fmt(a.get("congestedRoadKm")), fmt(a.get("crowdedRailKm"))));
        }
        md.append('\n');
    }

    private static String fmt(Stat s) {
        return String.format(Locale.US, "%.1f ± %.1f", s.mean(), s.ci95());
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
