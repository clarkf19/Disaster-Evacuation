package com.mumbai.evacuation;

import com.mumbai.evacuation.disaster.DisasterType;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.model.Shelter;
import com.mumbai.evacuation.service.DemoArrivalService;
import com.mumbai.evacuation.service.GraphService;
import com.mumbai.evacuation.service.ShelterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

// The scheduler is disabled (demo.enabled=false); the test drives tick() directly.
@SpringBootTest(properties = {"tomtom.api.key=", "llm.api-key=", "ratelimit.enabled=false", "demo.enabled=false"})
class DemoArrivalServiceTest {

    @Autowired DemoArrivalService demo;
    @Autowired GraphService graphService;
    @Autowired ShelterService shelterService;

    @AfterEach
    void reset() {
        graphService.clearAllDisasters();
        shelterService.getAllShelters().forEach(Shelter::resetOccupancy);
    }

    private int totalOccupancy() {
        return shelterService.getAllShelters().stream().mapToInt(Shelter::getCurrentOccupancy).sum();
    }

    @Test
    void sheltersFillWhileAHazardIsActiveAndEmptyAfterwards() {
        graphService.addDisaster(new DisasterRequest("t-flood", DisasterType.FLOOD, 19.0390, 72.8619, 1200.0, true, null, "test"));

        for (int i = 0; i < 10; i++) demo.tick();
        int filled = totalOccupancy();
        assertTrue(filled > 0, "shelters should receive arrivals");
        for (Shelter s : shelterService.getAllShelters()) {
            assertTrue(s.getCurrentOccupancy() <= s.getTotalCapacity(), s.getName() + " over capacity");
            if (graphService.getHazardOverlay().isShelterUnsafe(s)) {
                assertEquals(0, s.getCurrentOccupancy(), "unsafe shelter received people: " + s.getName());
            }
        }
        assertFalse(demo.getLastTickChange().isEmpty());

        graphService.clearAllDisasters();
        demo.tick();
        assertTrue(totalOccupancy() < filled, "shelters should drain once hazards are cleared");
    }

    @Test
    void arrivalsGoToTheNearestShelterFirst() {
        // BKC fire: the people should mostly end up close by, not across the city in Borivali.
        graphService.addDisaster(new DisasterRequest("t-fire", DisasterType.FIRE, 19.0657, 72.8686, 600.0, true, null, "test"));
        demo.tick();
        Shelter borivali = shelterService.getShelter(21).orElseThrow();
        assertEquals(0, borivali.getCurrentOccupancy());
    }
}
