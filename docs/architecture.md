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
- **Edge costs** are supplied through the `EdgeCost` interface and built per travel mode by `CostModel`.

## 2a. Multimodal network and travel modes
The graph holds four edge kinds:

| Kind | What | Used by |
|---|---|---|
| `ROAD` | Drivable OSM segment (respects one-way) | DRIVE, WALK, TRANSIT |
| `ROAD_REVERSE` | Opposite direction of a one-way road | WALK, TRANSIT (pedestrians ignore one-way rules) |
| `RAIL` | Mumbai Suburban Railway link between consecutive stations (both directions) | TRANSIT |
| `TRANSFER` | Station ↔ nearest road node (≤ 600 m); boarding includes a 5-minute average wait | TRANSIT |

`CostModel` costs per mode:
- **DRIVE**: free-flow time × hazard multiplier × traffic congestion.
- **WALK**: distance at 4.5 km/h × hazard multiplier. Walkers don't congest roads.
- **TRANSIT**: walking edges as WALK; RAIL = distance at 40 km/h + 30 s dwell, × hazard multiplier × crowding; TRANSFER = wait or exit time. TRANSIT also allows walking the whole way, so it is never slower than WALK.

A\* uses each mode's speed bound (fastest road / 4.5 km/h / 40 km/h) for an admissible heuristic.

## 2b. Data pipeline (`scripts/build_datasets.py`)
One reproducible script builds every dataset in `backend/src/main/resources/data/`:
- **Roads**: OSMnx drive network (motorway → residential), largest strongly connected component.
- **Elevation**: Copernicus GLO-30 heights sampled at every node, station, shelter and hotspot. This is a *surface* model, so dense built-up areas read somewhat high; it is used to rank ground as lower or higher, not as absolute water levels.
- **Rail**: all-stop Mumbai Suburban Railway route relations from OpenStreetMap, giving ordered stations on each line.
- **Shelters**: municipal schools from OpenStreetMap (BMC opens these as flood shelters). Capacity is estimated as campus area × 40 % built × 3 floors ÷ 3.5 m² per person (Sphere standard). A shelter is flagged flood-prone if it is low-lying or within 400 m of a chronic flooding spot.
- **Flood hotspots**: a curated list of chronic water-logging spots with approximate coordinates (not an official BMC list).

## 3. Hazard rules (`HazardOverlay`)
For each road segment and each hazard circle, using the distance from the circle centre to the **whole segment**:
- The segment doesn't touch the zone → unaffected.
- **Road-blocking hazard**, segment starts inside the zone and ends farther from the centre → *egress*, travel time × 2.0.
- **Road-blocking hazard**, any other touching segment → blocked.
- **Congestion hazard** → travel time × the hazard's multiplier.

Overlapping hazards: a segment blocked by any hazard is blocked, and otherwise the largest multiplier applies. A shelter is *unsafe* if it lies inside any zone, or if it is flood-prone while any flood is active.

**Water-logging:** while a flood is active, road and rail segments within max(2 km, 3 × flood radius) of it whose average elevation is ≤ 6 m are slowed ×1.3, or ×1.6 at ≤ 3 m. This makes routes prefer higher ground near floods. TomTom knows nothing about elevation, so during floods the route planner uses the graph route instead of live traffic.

## 4. Traffic model
Road capacity is in vehicles/hour and rail capacity in persons/hour (30,000 per line and direction). Drivers become a road flow of `persons / personsPerVehicle / windowHours`, and train riders a rail flow of `persons / windowHours`. The congestion (or crowding) factor comes from the load/capacity ratio R:

| R | ≤ 0.30 | ≤ 0.60 | ≤ 0.80 | > 0.80 |
|---|---|---|---|---|
| factor | 1.0 | 1.3 | 1.7 | 2.5 |

## 5. Evacuation strategies (`EvacuationEngine`)
Each group is split into walkers, train riders and drivers (default 50 / 30 / 20 %, configurable per run). Each part routes in its own mode. Groups are processed in priority order: groups inside a hazard zone first, then the largest groups first. Ties are broken by id, so results are deterministic.

**Naive nearest:** one Dijkstra pass per group finds the shelter that is fastest on hazard-only costs. Shelters admit arrivals in order of travel time until full, and everyone else is reported as turned away.

**Capacity-aware:** each group repeatedly takes the fastest shelter with free space, using costs that include the traffic already assigned, until the group is placed or no shelter is reachable. A group is split across shelters when one isn't enough. Afterwards, any allocation whose route is now ≥ 20 % slower than when it was assigned is re-routed with A\*, with its own traffic removed first.

Both strategies are scored under the final traffic they produce. Metrics: people housed, turned away or stranded, person-weighted average and worst travel time, average distance, shelters over capacity, km of congested road, and re-route count.

**Efficiency:** Dijkstra settles nodes in cost order, so `DijkstraEngine.searchToTargets(..., maxTargets = 1)` stops at the first shelter it reaches, which is exactly the nearest one. This works with hundreds of shelters without exploring the whole city.

## 5a. Statistical evaluation (`EvaluationService`)
- **Monte Carlo**: the scenario is re-run N times. Each group's size is scaled by U(1 − v, 1 + v) and its location moved up to J metres in a random direction. Both strategies run on the same randomised copy (a paired comparison). The output is mean, sd, min, max and the 95 % CI half-width (1.96·s/√n) for each metric, plus the share of runs where capacity-aware housed at least as many people.
- **Sensitivity**: one assumption at a time (people per vehicle, window, capacity scale, walk share, train share) is swept across a list of values. Each value uses the same seeds, so differences come from the parameter rather than from the random draw.
- Runs execute in parallel and every run is seeded, so results are reproducible.

## 6. A\* admissibility
h(n) = haversine(n, target) / (speed bound of the mode: fastest road limit for driving, 4.5 km/h for walking, 40 km/h for train + walk). Every cost overlay multiplies base time by a factor ≥ 1, and train links include a dwell time on top of running at 40 km/h. So h never overestimates, and A\* returns the same optimum as Dijkstra. `TravelModeTest` also checks this for the train mode. This is checked by `EvacuationEngineTest.aStarMatchesDijkstraOnRandomRealPairs` and by the `costsMatch` flag in `/api/benchmark/algorithms`.

## 7. Why recompute instead of D\* Lite?
A full A\* run on this graph takes a few milliseconds. Recomputing from scratch against the latest snapshot is simpler, provably optimal and free of stale-state bugs.

## 8. Security and operations
- `X-Admin-Token` is required for every non-GET request to `/api/disasters/**` and `/api/shelters/**` when `ADMIN_TOKEN` is set. The comparison is constant-time.
- Per-IP fixed-window rate limits apply to chat, live routes, search/geocode, simulations and the benchmark.
- Bean Validation runs on all request bodies, and a global handler returns JSON errors without stack traces.
- `/actuator/health` serves as the container health check. The Docker image runs as a non-root user.
