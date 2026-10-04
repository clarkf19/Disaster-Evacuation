package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.model.Shelter;
import com.mumbai.evacuation.service.GraphService;
import com.mumbai.evacuation.service.ShelterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Shelters with live occupancy. Reads are public; capacity/occupancy updates
 * require the operator token.
 */
@RestController
@RequestMapping("/api/shelters")
public class ShelterController {

    private final ShelterService shelterService;
    private final GraphService graphService;

    public record CapacityUpdate(@NotNull @Min(1) Integer totalCapacity) {}
    public record OccupancyUpdate(@NotNull @Min(0) Integer currentOccupancy) {}

    public ShelterController(ShelterService shelterService, GraphService graphService) {
        this.shelterService = shelterService;
        this.graphService = graphService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllShelters() {
        HazardOverlay overlay = graphService.getHazardOverlay();
        return shelterService.getAllShelters().stream().map(s -> shelterToMap(s, overlay)).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getShelter(@PathVariable long id) {
        return shelterToMap(find(id), graphService.getHazardOverlay());
    }

    @PostMapping("/{id}/capacity")
    public Map<String, Object> updateCapacity(@PathVariable long id, @Valid @RequestBody CapacityUpdate body) {
        Shelter s = find(id);
        s.setTotalCapacity(body.totalCapacity());
        return shelterToMap(s, graphService.getHazardOverlay());
    }

    @PostMapping("/{id}/occupancy")
    public Map<String, Object> updateOccupancy(@PathVariable long id, @Valid @RequestBody OccupancyUpdate body) {
        Shelter s = find(id);
        s.setCurrentOccupancy(body.currentOccupancy());
        return shelterToMap(s, graphService.getHazardOverlay());
    }

    @PostMapping("/reset")
    public Map<String, Object> resetAll() {
        shelterService.getAllShelters().forEach(Shelter::resetOccupancy);
        return Map.of("status", "ALL_OCCUPANCIES_RESET");
    }

    private Shelter find(long id) {
        return shelterService.getShelter(id).orElseThrow(() -> new NoSuchElementException("No shelter with id " + id));
    }

    private static Map<String, Object> shelterToMap(Shelter s, HazardOverlay overlay) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("lat", s.getLatitude());
        m.put("lon", s.getLongitude());
        m.put("totalCapacity", s.getTotalCapacity());
        m.put("currentOccupancy", s.getCurrentOccupancy());
        m.put("remainingCapacity", s.getRemainingCapacity());
        m.put("isFull", s.isFull());
        m.put("floodProne", s.isFloodProne());
        m.put("unsafe", overlay.isShelterUnsafe(s));
        return m;
    }
}
