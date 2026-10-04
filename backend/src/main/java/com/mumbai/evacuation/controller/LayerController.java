package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.service.FloodHotspotService;
import com.mumbai.evacuation.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** Map overlay data: monsoon flood hotspots and suburban railway stations. */
@RestController
@RequestMapping("/api")
public class LayerController {

    private final FloodHotspotService hotspotService;
    private final GraphService graphService;

    public LayerController(FloodHotspotService hotspotService, GraphService graphService) {
        this.hotspotService = hotspotService;
        this.graphService = graphService;
    }

    /** GET /api/flood-hotspots — chronic monsoon water-logging spots. */
    @GetMapping("/flood-hotspots")
    public Map<String, Object> floodHotspots() {
        return Map.of("verified", hotspotService.isVerified(), "hotspots", hotspotService.getHotspots());
    }

    /** GET /api/rail/stations — suburban stations, their lines, and whether hazards have closed them. */
    @GetMapping("/rail/stations")
    public List<Map<String, Object>> stations() {
        Graph graph = graphService.getGraph();
        HazardOverlay overlay = graphService.getHazardOverlay();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Node station : graph.getStations()) {
            Set<String> lines = new TreeSet<>();
            boolean open = false;
            for (Edge e : graph.getOutgoingEdges(station.getId())) {
                if (e.getKind() == Edge.Kind.RAIL) lines.add(e.getLine());
                // A station is usable if people can still get in or out of it.
                if (e.getKind() == Edge.Kind.TRANSFER && !overlay.isBlocked(e)) open = true;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", station.getId());
            m.put("name", station.getName());
            m.put("lat", station.getLatitude());
            m.put("lon", station.getLongitude());
            m.put("lines", lines);
            m.put("open", open);
            out.add(m);
        }
        return out;
    }
}
