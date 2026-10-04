package com.mumbai.evacuation;

import com.mumbai.evacuation.dto.EvaluationDtos.*;
import com.mumbai.evacuation.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"tomtom.api.key=", "llm.api-key=", "ratelimit.enabled=false", "demo.enabled=false"})
class EvaluationServiceTest {

    @Autowired EvaluationService evaluation;

    private MonteCarloRequest request(int runs, long seed) {
        return new MonteCarloRequest("western_suburbs_flood", runs, seed, 0.3, 500.0, null, null, null, null, null);
    }

    @Test
    void monteCarloReportsStatisticsForBothStrategies() {
        MonteCarloResult r = evaluation.monteCarlo(request(8, 7));
        assertEquals(8, r.runs());
        assertEquals(16, r.perRun().size());
        for (String strategy : List.of("NAIVE_NEAREST", "CAPACITY_AWARE")) {
            Stat housed = r.metrics().get(strategy).get("housedPercent");
            assertEquals(8, housed.n());
            assertTrue(housed.min() <= housed.mean() && housed.mean() <= housed.max());
            assertTrue(housed.ci95() >= 0);
        }
        // On the stress test, capacity-aware assignment should house more people on average.
        assertTrue(r.metrics().get("CAPACITY_AWARE").get("housedPercent").mean()
                > r.metrics().get("NAIVE_NEAREST").get("housedPercent").mean());
    }

    @Test
    void sameSeedGivesTheSameResult() {
        MonteCarloResult a = evaluation.monteCarlo(request(4, 99));
        MonteCarloResult b = evaluation.monteCarlo(request(4, 99));
        assertEquals(a.metrics(), b.metrics());
    }

    @Test
    void moreShelterCapacityNeverHousesFewerPeople() {
        SensitivityResult r = evaluation.sensitivity(new SensitivityRequest("western_suburbs_flood",
                Parameter.CAPACITY_SCALE, List.of(0.5, 1.0, 2.0), 3, 1L, 0.2, 300.0));
        assertEquals(3, r.points().size());
        double previous = -1;
        for (SensitivityPoint p : r.points()) {
            double housed = p.metrics().get("CAPACITY_AWARE").get("housedPercent").mean();
            assertTrue(housed >= previous - 1e-9, "housed% fell when capacity grew: " + housed + " < " + previous);
            previous = housed;
        }
    }

    @Test
    void oversizedSweepsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> evaluation.sensitivity(new SensitivityRequest(
                "sion_flood", Parameter.WINDOW_HOURS, List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0), 30, 1L, null, null)));
    }
}
