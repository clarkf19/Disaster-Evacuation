package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.model.Shelter;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for shelter listing and nearest-shelter routing.
 */
@RestController
@RequestMapping("/api/shelters")
@CrossOrigin(origins = "*")
public class ShelterController {

    @Autowired
    private GraphService graphService;

    /** GET /api/shelters — list all shelters with capacity */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllShelters() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Shelter s : graphService.getShelterService().getAllShelters()) {
            result.add(shelterToMap(s));
        }
        return ResponseEntity.ok(result);
    }

    /** GET /api/shelters/{id} — single shelter detail */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getShelter(@PathVariable long id) {
        Shelter s = graphService.getShelterService().getShelter(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(shelterToMap(s));
    }

    /** POST /api/shelters/{id}/capacity — update capacity */
    @PostMapping("/{id}/capacity")
    public ResponseEntity<Map<String, Object>> updateCapacity(
            @PathVariable long id, @RequestBody Map<String, Integer> body) {
        Shelter s = graphService.getShelterService().getShelter(id);
        if (s == null) return ResponseEntity.notFound().build();
        if (body.containsKey("totalCapacity")) {
            s.setTotalCapacity(body.get("totalCapacity"));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "CAPACITY_UPDATED");
        resp.put("shelterId", id);
        resp.put("newCapacity", s.getTotalCapacity());
        return ResponseEntity.ok(resp);
    }

    /** POST /api/shelters/reset — reset all occupancies */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAll() {
        graphService.getShelterService().getAllShelters().forEach(Shelter::resetOccupancy);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ALL_OCCUPANCIES_RESET");
        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> shelterToMap(Shelter s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("lat", s.getLatitude());
        m.put("lon", s.getLongitude());
        m.put("nearestNodeId", s.getNearestNodeId());
        m.put("totalCapacity", s.getTotalCapacity());
        m.put("currentOccupancy", s.getCurrentOccupancy());
        m.put("remainingCapacity", s.getRemainingCapacity());
        m.put("isFull", s.isFull());
        return m;
    }
}
