# 🌊 Mumbai Disaster Evacuation Route Planning & Optimization System

> Hazard-aware evacuation routing (drive, walk, or walk + suburban train) and capacity-aware shelter assignment for Greater Mumbai. It runs on a real OpenStreetMap network: 94,758 road nodes, 215,698 directed road segments including residential streets, 67 suburban stations, and elevation for every node.

⚠️ **Student / research project — not an official emergency service.** Shelters are municipal schools and open grounds from OpenStreetMap with *estimated* capacities, not the official BMC list. In an emergency call **112** or BMC **1916**.

---

## 🌟 Features

### 0. 🗺️ Real data, rebuilt with one script
`scripts/build_datasets.py` regenerates every dataset from open sources:

| Dataset | Source | Notes |
|---|---|---|
| Road network (drivable + residential streets) | OpenStreetMap via OSMnx | Largest strongly connected component, so every node can reach every other |
| Elevation of every node, station and shelter | Copernicus DEM GLO-30 | A surface model: good for telling low ground from high, less so for absolute heights |
| Suburban railway | OpenStreetMap route relations | Western, Central, Harbour, Trans-Harbour and more, with stations in line order |
| Shelters | OpenStreetMap municipal schools + large open grounds | Capacity estimated from campus area (Sphere 3.5 m²/person); open grounds aren't used during floods |
| Monsoon flooding hotspots | Curated list (approximate locations) | Chronic water-logging spots; shown as a map layer and usable as a scenario |

### 1. ⚡ Hazard-aware route planner — drive, walk or train
- **Three travel modes.**
  - 🚶 **Walk:** one-way streets don't apply.
  - 🚆 **Train:** walk to a suburban station, wait, ride, walk on. The route shows a step-by-step itinerary like "Western Line from Andheri to Dadar, 6 stops".
  - 🚗 **Drive:** uses live traffic when TomTom is configured.
- **Elevation-aware during floods.** Low-lying roads and rail near an active flood are slowed (they're likely water-logged), so routes prefer higher ground. The card shows the lowest point on your route and warns if it's low. Flooded stations close, and trains won't stop there.
- Routes **always stay out of active hazard zones**. With a TomTom key, the backend asks TomTom for a live-traffic route with every hazard zone passed as an *avoid-area*, then **verifies the geometry server-side**. Without a key, if TomTom fails, or if the route would enter a zone, it uses our own A* on the road graph with hazards applied.
- **People inside a hazard zone can still get out.** Road-blocking hazards block roads leading *into* or *through* the zone, while roads leading *away* from the centre stay passable with a slow-down penalty.
- Whole road segments are tested against hazard circles (not just their midpoints), so long segments that cross a zone are caught.
- If a hazard zone is added or removed while a route is on screen, the route is re-planned automatically. If no passable route exists, the app says so instead of drawing a straight line.

### 2. 🚨 Live hazard zones (operator-controlled)
- Four hazard types: flood, fire, bridge collapse and chemical leak. Each one either **blocks roads** or adds **heavy congestion** (a travel-time multiplier).
- Live hazards affect routing for everyone, so adding or removing them requires an **operator token** (`ADMIN_TOKEN`).
- **"Heavy monsoon day"** button: floods every chronic water-logging spot at once.
- Shelters inside a hazard zone (or flood-prone sites during a flood) are flagged **unsafe** and excluded.
- **Demo mode** (on by default) brings the shelters to life. Each active zone gets an estimated number of people needing shelter (zone area × density × share evacuating). Every few seconds a batch of them arrives at the nearest safe shelters by road travel time, spilling over when a shelter fills. When hazards are cleared, shelters gradually empty. This data is simulated and labelled as such in the UI.

### 3. 📊 Command Centre: evacuation simulation
Runs the same scenario through two strategies and compares them side by side on the map:

| | Naive nearest | Capacity-aware |
|---|---|---|
| Shelter choice | Fastest shelter on empty roads | Fastest shelter **with free space**, given traffic already assigned |
| Capacity | Ignored; shelters admit first-come-first-served and turn the rest away | Respected; large groups are **split** across shelters |
| Traffic | Ignored when choosing routes | Each assignment adds vehicles/hour to its roads; routes slowed ≥ 20 % are **re-routed** |

**How Mumbai moves:** each group splits into walkers, train riders and drivers (default 50 / 30 / 20 %, adjustable with sliders).
- **Drivers** load the roads: people → vehicles/hour, compared with road capacity.
- **Train riders** load the suburban lines: persons/hour, compared with line capacity, so crowded trains slow down.
- **Walkers** are assumed not to congest roads.

Results are broken down by mode. Simulations run in a **sandbox** and never touch live hazards or shelter occupancy. Six presets are included: a western-suburbs stress test where the naive strategy overflows, and a "heavy monsoon day" with every chronic flooding spot underwater.

### 3a. 📈 Evaluation: statistics, not single runs
- **Monte Carlo:** re-runs a scenario N times with randomised group sizes (± %) and locations (within J m). Both strategies run on each copy, and the output is the **mean ± 95 % confidence interval**, standard deviation and min/max for every metric, plus how often capacity-aware wins. A per-run CSV export is included for your own analysis.
- **Sensitivity analysis:** sweeps one assumption (people per vehicle, evacuation window, shelter capacity, walking share, train share) and charts how each strategy responds, with error bars.
- Everything is seeded, so results are reproducible.
- **Published results:** [`docs/evaluation-results.md`](docs/evaluation-results.md) has 30 randomised runs for every preset plus four sensitivity sweeps. Regenerate it with:
  ```bash
  cd backend && ./mvnw test -Dsurefire.excludedGroups= -Dgroups=report
  ```
  Headline: across all six scenarios, capacity-aware assignment housed at least as many people as the naive plan in 100 % of runs. On the western-suburbs stress test it housed 83.6 ± 1.1 % of evacuees, versus 3.9 ± 0.6 % for naive.

The Command Centre also benchmarks **Dijkstra vs A\*** on long corridors. Both return the same optimal travel time, and A\* explores far fewer nodes.

### 4. 🤖 Emergency AI assistant
- Google Gemini, with a Mumbai-specific system instruction, the current hazards and the nearest **open, safe** shelters to the user's start location.
- Falls back to built-in, keyword-based guidance when no key is configured or the API fails.
- Voice input (Web Speech API). It is clearly labelled as an AI helper, not an official service.

### 5. 🗺️ Place search & reverse geocoding
- Photon (OSM) autocomplete limited to the mapped area, with TomTom as a fallback. Reverse geocoding uses Nominatim. All of it is **proxied through the backend** (with caching and an identifying User-Agent), so it works the same in development and production.

### 6. 🛡️ Protection guides & hospital directory
First-aid steps, do's and don'ts, emergency kits and hospital contacts for each hazard type. The frontend keeps a built-in copy, so these still work if the backend is down.

---

## 🛠️ Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Bean Validation, Actuator |
| Frontend | React 18, Vite 5, React-Leaflet, CSS Modules, Vitest |
| Data | Python 3, OSMnx, Rasterio (`scripts/build_datasets.py`) |
| Services | TomTom Routing/Search (optional), Google Gemini (optional), Photon, Nominatim |
| CI | GitHub Actions (backend `mvn verify`, frontend tests + build) |

---

## 📂 Project structure

```
backend/
  src/main/java/com/mumbai/evacuation/
    algorithm/   Dijkstra, A*, multi-target search, EdgeCost overlays
    disaster/    DisasterEvent, HazardOverlay (blocking/egress/congestion rules), DisasterEngine
    model/       Immutable Graph (with grid spatial index), Edge, Node, Shelter
    service/     GraphService, LiveRouteService, TomTomService, GeocodingService,
                 EvacuationEngine, EmergencyChatbotService, ShelterService
    controller/  REST controllers
    config/      Operator-token auth, CORS, rate limiting, JSON error handling
  src/main/resources/data/   mumbai_nodes.csv, mumbai_edges.csv, shelters.json
  src/test/java/             JUnit tests (algorithms, hazards, simulator, API)
frontend/
  src/components/  RoutePlanner, DisasterPanel, ShelterPanel, CommandCentre, MapView, EmergencyChatbot
  src/services/    backendApi.js (all HTTP calls), routingApi.js (+ tests)
scripts/           OSM graph extractor, JMeter load test
docs/              Architecture and deployment guides
```

---

## ⚙️ Getting started

**Prerequisites:** JDK 17+, Node.js 18+ (Maven is bundled via `mvnw`).

1. *(Optional)* Copy `.env.example` to `.env` in the repo root and add keys. Every key is optional:
   ```bash
   cp .env.example .env
   ```
   The backend reads `.env` automatically when started from `backend/`.

2. Start the backend (port 8080):
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```
   On Windows, use `mvnw.cmd spring-boot:run`.

3. Start the frontend (port 5173, proxies `/api` to the backend):
   ```bash
   cd frontend && npm install && npm run dev
   ```

### Tests
```bash
cd backend && ./mvnw test
```
```bash
cd frontend && npm test
```

### Environment variables

| Variable | Purpose |
|---|---|
| `TOMTOM_API_KEY` | Live traffic for the route planner (optional) |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | AI assistant (optional; model is configurable because Google retires models) |
| `ADMIN_TOKEN` | Operator token for changing live hazards and shelters. **Required in any deployment.** When empty, those endpoints are open (local development only). |
| `CORS_ALLOWED_ORIGINS` | Only needed if browsers call the backend directly instead of through `/api` |
| `EVAC_PERSONS_PER_VEHICLE`, `EVAC_WINDOW_HOURS` | Simulation traffic model |
| `DEMO_MODE` | Simulated shelter arrivals from active hazard zones (default `true`; operators can toggle it in the Shelters tab). Turn off once real check-in data is connected. |
| `RATE_LIMIT_ENABLED` | Per-IP rate limits for chat, routing, search and simulations (default `true`) |

---

## 📡 API summary

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/live-route` | Hazard-aware route `{fromLat, fromLon, toLat, toLon, mode?}` — mode `DRIVE` / `WALK` / `TRANSIT` |
| `GET` | `/api/flood-hotspots` · `/api/rail/stations` | Monsoon flooding spots / suburban stations (with open/closed status) |
| `POST` | `/api/disasters/monsoon` | 🔒 Flood every chronic water-logging spot |
| `POST` | `/api/evaluation/monte-carlo` | Repeated randomised runs → mean, sd, 95 % CI per metric and strategy |
| `POST` | `/api/evaluation/sensitivity` | Sweep one assumption (`PERSONS_PER_VEHICLE`, `WINDOW_HOURS`, `CAPACITY_SCALE`, `WALK_SHARE`, `TRANSIT_SHARE`) |
| `GET` | `/api/evacuation/defaults` | Default simulation assumptions |
| `GET` | `/api/search?q=` · `/api/geocode?lat=&lon=` | Place autocomplete / reverse geocoding |
| `GET` | `/api/shelters` | Shelters with occupancy and `unsafe` flag |
| `POST` | `/api/shelters/{id}/capacity` · `/occupancy` | 🔒 Update shelter capacity / occupancy |
| `GET` | `/api/disasters` | Active hazard zones |
| `POST` / `DELETE` | `/api/disasters`, `/api/disasters/{id}` | 🔒 Add / remove hazard zones |
| `GET` | `/api/evacuation/scenarios` | Preset scenarios |
| `POST` | `/api/evacuation/compare` | Run both strategies `{scenarioId}` or `{groups, disasters}` |
| `POST` | `/api/evacuation/simulate?strategy=` | Run one strategy |
| `GET` | `/api/benchmark/algorithms` | Dijkstra vs A\* |
| `POST` | `/api/route` | Node-to-node route on the graph |
| `GET` | `/api/nearest?lat=&lon=` | Nearest road node and its distance |
| `POST` | `/api/chat` | Emergency AI assistant `{message, userLat?, userLon?}` |
| `GET` | `/api/config` · `/actuator/health` | Capabilities / health check |

🔒 = requires the `X-Admin-Token` header when `ADMIN_TOKEN` is set. Errors come back as JSON: `{status, error, message, details?}`.

---

## 🗺️ Data & known limitations

- **Regenerating data:** the data needs Python 3.10+ plus the packages in `scripts/requirements.txt`. Create a virtual environment and install them:
  ```bash
  python -m venv .venv && .venv/Scripts/pip install -r scripts/requirements.txt
  ```
  Then run:
  ```bash
  .venv/Scripts/python scripts/build_datasets.py
  ```
  Options: `--bbox W S E N` covers a different area (e.g. add Mira-Bhayandar or more of Navi Mumbai); `--no-residential` gives a smaller, faster graph. Downloads are cached in `scripts/.cache/` (gitignored, safe to delete).
- **Shelters** are municipal schools mapped in OpenStreetMap (incomplete — BMC runs many more) plus open grounds. Capacities are estimates. Swap in the official BMC/MCGM list when you have it; the JSON format is documented in the file.
- **Elevation** is a surface model (it includes buildings), so treat it as relative. The water-logging penalty is a modelling assumption, not a hydrological simulation.
- **Flood hotspots** are approximate and not an official list.
- **Trains** use average speeds and a fixed emergency capacity (30,000 people/hour per line and direction), not real timetables.
- **Hospital and helpline numbers** in the guides should be re-verified periodically.
- Live state (hazards, occupancy) is in memory and single-instance. It resets on restart.

---

## 📜 Acknowledgements
Road data © OpenStreetMap contributors (via OSMnx). Geocoding by Komoot Photon and Nominatim. Map tiles © CARTO. Optional live traffic by TomTom, AI by Google Gemini.
