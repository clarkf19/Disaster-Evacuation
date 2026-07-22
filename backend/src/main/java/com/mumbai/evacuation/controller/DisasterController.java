package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for injecting and managing disaster events.
 *
 * Design Decision:
 * - Disasters are applied synchronously to the in-memory graph.
 * - After any disaster mutation, the client should re-query /api/route to
 *   get the dynamically updated (recomputed from scratch) shortest path.
 * - Route recomputation from scratch is guaranteed correct; incremental
 *   update strategies (D* Lite) were intentionally not used — see DisasterEngine for rationale.
 */
@RestController
@RequestMapping("/api/disasters")
@CrossOrigin(origins = "*")
public class DisasterController {

    @Autowired
    private GraphService graphService;

    /** POST /api/disasters — inject a disaster event */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addDisaster(@RequestBody DisasterRequest req) {
        if (req.getId() == null || req.getId().isBlank()) {
            req.setId("disaster-" + System.currentTimeMillis());
        }
        graphService.addDisaster(req);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "DISASTER_APPLIED");
        resp.put("disasterId", req.getId());
        resp.put("type", req.getType());
        resp.put("affectedRadiusMeters", req.getRadiusMeters());
        resp.put("blockRoads", req.isBlockRoads());
        return ResponseEntity.ok(resp);
    }

    /** DELETE /api/disasters/{id} — remove a specific disaster */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> removeDisaster(@PathVariable String id) {
        graphService.removeDisaster(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "DISASTER_REMOVED");
        resp.put("disasterId", id);
        return ResponseEntity.ok(resp);
    }

    /** DELETE /api/disasters — clear all disasters */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAll() {
        graphService.clearAllDisasters();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ALL_DISASTERS_CLEARED");
        return ResponseEntity.ok(resp);
    }

    /** GET /api/disasters — list active disasters */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDisasters() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DisasterEvent d : graphService.getActiveDisasters()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("type", d.getType().name());
            m.put("lat", d.getCenterLatitude());
            m.put("lon", d.getCenterLongitude());
            m.put("radiusMeters", d.getAffectedRadiusMeters());
            m.put("blockRoads", d.isBlockRoads());
            m.put("congestionMultiplier", d.getCongestionMultiplier());
            m.put("description", d.getDescription());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}
