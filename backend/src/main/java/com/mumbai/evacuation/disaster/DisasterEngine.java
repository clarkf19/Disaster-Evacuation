package com.mumbai.evacuation.disaster;

import com.mumbai.evacuation.model.Graph;

import java.util.*;

/**
 * Holds the live set of active disasters and the {@link HazardOverlay} derived
 * from it.
 *
 * Writers rebuild the overlay under a lock and publish it through a volatile
 * reference; readers (route queries) just grab the current snapshot, so a query
 * always sees one consistent set of blockages and never a half-applied update.
 *
 * Routes are recomputed from scratch against the new snapshot rather than
 * repaired incrementally (D* Lite): on an ~9k-node graph a full A* run takes a
 * few milliseconds, which keeps the code simple and the results provably optimal.
 */
public class DisasterEngine {

    private final Graph graph;
    private final Map<String, DisasterEvent> activeDisasters = new LinkedHashMap<>();
    private volatile HazardOverlay overlay = HazardOverlay.empty();

    public DisasterEngine(Graph graph) {
        this.graph = graph;
    }

    public synchronized void addDisaster(DisasterEvent event) {
        activeDisasters.put(event.getId(), event);
        rebuild();
    }

    /** @return false if no disaster with that id existed */
    public synchronized boolean removeDisaster(String disasterId) {
        if (activeDisasters.remove(disasterId) == null) return false;
        rebuild();
        return true;
    }

    public synchronized void clearAllDisasters() {
        activeDisasters.clear();
        rebuild();
    }

    /** Current immutable snapshot (disasters + their effect on roads). */
    public HazardOverlay getOverlay() {
        return overlay;
    }

    public List<DisasterEvent> getActiveDisasters() {
        return overlay.getDisasters();
    }

    private void rebuild() {
        overlay = HazardOverlay.build(graph, activeDisasters.values());
    }
}
