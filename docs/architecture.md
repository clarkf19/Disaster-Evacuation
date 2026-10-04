# Mumbai Disaster Evacuation System — Architecture

## 1. Overview
A Spring Boot backend holds an immutable in-memory road graph (from OpenStreetMap) and the live set of hazard zones, and answers hazard-aware routing queries and sandboxed evacuation simulations. A React + Leaflet frontend talks only to the backend's `/api`. All third-party services (TomTom, Photon, Nominatim, Gemini) are called server-side.

```
Browser ──/api──▶ Vercel rewrite / Vite proxy ──▶ Spring Boot
                                                   ├─ GraphService      (graph, live hazards, A*/Dijkstra)
                                                   ├─ LiveRouteService  (TomTom + verification, graph fallback)
                                                   ├─ EvacuationEngine  (sandboxed simulations)
                                                   ├─ GeocodingService  (Photon / Nominatim / TomTom, cached)
                                                   └─ EmergencyChatbotService (Gemini + offline fallback)
```

## 2. State and concurrency
- **Graph**: built once at startup and never mutated, so it is safe to share across request threads without locks. A uniform grid (~550 m cells) indexes nodes for nearest-node lookups.
- **Hazards**: `DisasterEngine` keeps the active disasters. Every change rebuilds an immutable `HazardOverlay` under a lock and publishes it through a `volatile` reference. A route query takes one snapshot and uses it throughout, so it never sees a half-applied update.
- **Simulations**: each run builds its own overlay, traffic map and shelter ledger. Nothing is written to shared state.
- **Edge costs** are supplied through the `EdgeCost` interface: free flow × hazard multiplier × traffic congestion factor, or ∞ when blocked.

## 3. Hazard rules (`HazardOverlay`)
For each road segment and each hazard circle, using the distance from the circle centre to the **whole segment**:
- The segment doesn't touch the zone → unaffected.
- **Road-blocking hazard**, segment starts inside the zone and ends farther from the centre → *egress*, travel time × 2.0.
- **Road-blocking hazard**, any other touching segment → blocked.
- **Congestion hazard** → travel time × the hazard's multiplier.

Overlapping hazards: a segment blocked by any hazard is blocked, and otherwise the largest multiplier applies. A shelter is *unsafe* if it lies inside any zone, or if it is flood-prone while any flood is active.

## 4. Traffic model
Edge capacity is in vehicles/hour. Evacuees become a flow of `persons / personsPerVehicle / windowHours`, and the congestion factor is taken from the volume/capacity ratio R:

| R | ≤ 0.30 | ≤ 0.60 | ≤ 0.80 | > 0.80 |
|---|---|---|---|---|
| factor | 1.0 | 1.3 | 1.7 | 2.5 |

## 5. Evacuation strategies (`EvacuationEngine`)
Groups are processed in priority order: groups inside a hazard zone first, then the largest groups first. Ties are broken by id, so results are deterministic.

**Naive nearest:** one Dijkstra pass per group finds the shelter that is fastest on hazard-only costs. Shelters admit arrivals in order of travel time until full, and everyone else is reported as turned away.

**Capacity-aware:** each group repeatedly takes the fastest shelter with free space, using costs that include the traffic already assigned, until the group is placed or no shelter is reachable. A group is split across shelters when one isn't enough. Afterwards, any allocation whose route is now ≥ 20 % slower than when it was assigned is re-routed with A\*, with its own traffic removed first.

Both strategies are scored under the final traffic they produce. Metrics: people housed, turned away or stranded, person-weighted average and worst travel time, average distance, shelters over capacity, km of congested road, and re-route count.

**Efficiency:** `DijkstraEngine.searchToTargets` stops once every candidate shelter node is settled, giving the costs to all shelters from one search instead of one A\* run per shelter.

## 6. A\* admissibility
h(n) = haversine(n, target) / (fastest speed limit in the graph). Every cost overlay multiplies free-flow time by a factor ≥ 1, so h never overestimates and A\* returns the same optimum as Dijkstra. This is checked by `EvacuationEngineTest.aStarMatchesDijkstraOnRandomRealPairs` and by the `costsMatch` flag in `/api/benchmark/algorithms`.

## 7. Why recompute instead of D\* Lite?
A full A\* run on this graph takes a few milliseconds. Recomputing from scratch against the latest snapshot is simpler, provably optimal and free of stale-state bugs.

## 8. Security and operations
- `X-Admin-Token` is required for every non-GET request to `/api/disasters/**` and `/api/shelters/**` when `ADMIN_TOKEN` is set. The comparison is constant-time.
- Per-IP fixed-window rate limits apply to chat, live routes, search/geocode, simulations and the benchmark.
- Bean Validation runs on all request bodies, and a global handler returns JSON errors without stack traces.
- `/actuator/health` serves as the container health check. The Docker image runs as a non-root user.
