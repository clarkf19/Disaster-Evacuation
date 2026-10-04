package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.config.AdminTokenInterceptor;
import com.mumbai.evacuation.dto.RouteRequest;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.service.DemoArrivalService;
import com.mumbai.evacuation.service.GraphService;
import com.mumbai.evacuation.service.ShelterService;
import com.mumbai.evacuation.service.TomTomService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Road-graph routing, graph metadata and app capability info. */
@RestController
@RequestMapping("/api")
public class RouteController {

    private final GraphService graphService;
    private final ShelterService shelterService;
    private final TomTomService tomTomService;
    private final AdminTokenInterceptor adminTokenInterceptor;
    private final DemoArrivalService demoArrivalService;

    public RouteController(GraphService graphService, ShelterService shelterService, TomTomService tomTomService,
                           AdminTokenInterceptor adminTokenInterceptor, DemoArrivalService demoArrivalService) {
        this.graphService = graphService;
        this.shelterService = shelterService;
        this.tomTomService = tomTomService;
        this.adminTokenInterceptor = adminTokenInterceptor;
        this.demoArrivalService = demoArrivalService;
    }

    /** POST /api/route — node-to-node route on the road graph under the live hazard overlay. */
    @PostMapping("/route")
    public RouteResponse computeRoute(@Valid @RequestBody RouteRequest request) {
        return graphService.computeRoute(request.sourceNodeId(), request.targetNodeId(), request.algorithm());
    }

    /** GET /api/graph/stats — graph size, coverage bounds and live hazard impact. */
    @GetMapping("/graph/stats")
    public Map<String, Object> graphStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        var graph = graphService.getGraph();
        stats.put("roadNodeCount", graph.getRoadNodeCount());
        stats.put("roadEdgeCount", graph.getEdgeCount(com.mumbai.evacuation.model.Edge.Kind.ROAD));
        stats.put("walkOnlyEdgeCount", graph.getEdgeCount(com.mumbai.evacuation.model.Edge.Kind.ROAD_REVERSE));
        stats.put("stationCount", graph.getStations().size());
        stats.put("railEdgeCount", graph.getEdgeCount(com.mumbai.evacuation.model.Edge.Kind.RAIL));
        stats.put("bounds", graph.getBounds());
        stats.put("blockedEdges", graphService.getHazardOverlay().getBlockedEdgeCount());
        stats.put("lowLyingEdgesSlowed", graphService.getHazardOverlay().getLowLyingEdgeCount());
        return stats;
    }

    /** GET /api/nearest?lat=&lon= — nearest road-network node and how far away it is. */
    @GetMapping("/nearest")
    public Map<String, Object> nearestNode(@RequestParam double lat, @RequestParam double lon) {
        GraphService.Snap snap = graphService.snap(lat, lon);
        if (snap.node() == null) throw new NoSuchElementException("Road graph is empty");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", snap.node().getId());
        resp.put("lat", snap.node().getLatitude());
        resp.put("lon", snap.node().getLongitude());
        resp.put("distanceMeters", Math.round(snap.distanceMeters()));
        resp.put("withinCoverage", snap.withinCoverage());
        return resp;
    }

    /** GET /api/config — what this deployment supports, so the UI can adapt. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("liveTrafficEnabled", tomTomService.isConfigured());
        cfg.put("operatorTokenRequired", adminTokenInterceptor.isRequired());
        cfg.put("shelterDataVerified", shelterService.isDataVerified());
        cfg.put("coverageBounds", graphService.getGraph().getBounds());
        cfg.put("demoMode", demoArrivalService.isEnabled());
        return cfg;
    }
}
