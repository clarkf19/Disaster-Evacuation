package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.dto.DisasterRequest;
import com.mumbai.evacuation.dto.EvacuationBenchmarkResult.DisasterView;
import com.mumbai.evacuation.service.GraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Live disaster management. Mutations require the operator token (see
 * AdminTokenInterceptor) because they change routing for every user.
 * After a change, clients simply re-request their route: the backend always
 * routes against the latest hazard snapshot.
 */
@RestController
@RequestMapping("/api/disasters")
public class DisasterController {

    private final GraphService graphService;

    public DisasterController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public List<DisasterView> listDisasters() {
        return graphService.getActiveDisasters().stream().map(DisasterController::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisasterView addDisaster(@Valid @RequestBody DisasterRequest request) {
        return view(graphService.addDisaster(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDisaster(@PathVariable String id) {
        if (!graphService.removeDisaster(id)) {
            throw new NoSuchElementException("No active disaster with id " + id);
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll() {
        graphService.clearAllDisasters();
    }

    static DisasterView view(DisasterEvent d) {
        return new DisasterView(d.getId(), d.getType().name(), d.getCenterLatitude(), d.getCenterLongitude(),
                d.getAffectedRadiusMeters(), d.isBlockRoads(), d.getCongestionMultiplier(), d.getDescription());
    }
}
