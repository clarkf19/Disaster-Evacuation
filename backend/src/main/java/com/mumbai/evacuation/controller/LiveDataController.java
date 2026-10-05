package com.mumbai.evacuation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/live — everything the UI polls (config, shelters, hazards, stations)
 * in ONE response, so each refresh costs a single round trip instead of four.
 * Over a Vercel → Render hop that is the difference between ~0.4 s and ~1.6 s.
 */
@RestController
public class LiveDataController {

    private final RouteController routeController;
    private final ShelterController shelterController;
    private final DisasterController disasterController;
    private final LayerController layerController;

    public LiveDataController(RouteController routeController, ShelterController shelterController,
                              DisasterController disasterController, LayerController layerController) {
        this.routeController = routeController;
        this.shelterController = shelterController;
        this.disasterController = disasterController;
        this.layerController = layerController;
    }

    @GetMapping("/api/live")
    public Map<String, Object> live() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("config", routeController.config());
        m.put("shelters", shelterController.getAllShelters());
        m.put("disasters", disasterController.listDisasters());
        m.put("stations", layerController.stations());
        return m;
    }
}
