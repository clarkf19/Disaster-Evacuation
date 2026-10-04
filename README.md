# 🌊 Mumbai Disaster Evacuation Route Planning & Optimization System

> Hazard-aware evacuation routing and capacity-aware shelter assignment for Greater Mumbai, on a real OpenStreetMap road graph (8,851 nodes · 17,186 directed road segments).

⚠️ **Student / research project — not an official emergency service.** Shelter sites and capacities are placeholder data, not the official BMC list. In an emergency call **112** or BMC **1916**.

---

## 🌟 Features

### 1. ⚡ Hazard-aware route planner
- Routes **always stay out of active hazard zones**. With a TomTom key, the backend asks TomTom for a live-traffic route with every hazard zone passed as an *avoid-area*, then **verifies the geometry server-side**. Without a key, if TomTom fails, or if the route would enter a zone, it uses our own A* on the road graph with hazards applied.
- **People inside a hazard zone can still get out.** Road-blocking hazards block roads leading *into* or *through* the zone, while roads leading *away* from the centre stay passable with a slow-down penalty.
- Whole road segments are tested against hazard circles (not just their midpoints), so long segments that cross a zone are caught.
- If a hazard zone is added or removed while a route is on screen, the route is re-planned automatically. If no passable route exists, the app says so instead of drawing a straight line.

### 2. 🚨 Live hazard zones (operator-controlled)
- Four hazard types: flood, fire, bridge collapse and chemical leak. Each one either **blocks roads** or adds **heavy congestion** (a travel-time multiplier).
- Live hazards affect routing for everyone, so adding or removing them requires an **operator token** (`ADMIN_TOKEN`).
- Shelters inside a hazard zone (or flood-prone sites during a flood) are flagged **unsafe** and excluded.

### 3. 📊 Command Centre: evacuation simulation
Runs the same scenario through two strategies and compares them side by side on the map:

| | Naive nearest | Capacity-aware |
|---|---|---|
| Shelter choice | Fastest shelter on empty roads | Fastest shelter **with free space**, given traffic already assigned |
| Capacity | Ignored; shelters admit first-come-first-served and turn the rest away | Respected; large groups are **split** across shelters |
| Traffic | Ignored when choosing routes | Each assignment adds vehicles/hour to its roads; routes slowed ≥ 20 % are **re-routed** |

Both strategies are scored against the same traffic model: people → vehicles/hour (`EVAC_PERSONS_PER_VEHICLE`, `EVAC_WINDOW_HOURS`) compared with each road's capacity. Simulations run in a **sandbox** and never touch live hazards or shelter occupancy. Five presets are included, among them a western-suburbs stress test where the naive strategy overflows its shelters.

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
| Data | Python 3 + OSMnx (`scripts/extract_mumbai_graph.py`) |
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
| `RATE_LIMIT_ENABLED` | Per-IP rate limits for chat, routing, search and simulations (default `true`) |

---

## 📡 API summary

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/live-route` | Hazard-aware route `{fromLat, fromLon, toLat, toLon}` |
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

- **Coverage:** the road graph spans roughly Colaba to Thane (lat 18.90–19.32, lon 72.78–73.00) and includes motorway to tertiary roads only. Points more than 2 km from the network are reported as out of coverage. To extend it (e.g. Navi Mumbai, Mira-Bhayandar, residential streets), regenerate the graph:
  ```bash
  python scripts/extract_mumbai_graph.py --bbox 72.75 18.88 73.15 19.50 --residential
  ```
- **Shelters** (`backend/src/main/resources/data/shelters.json`) are unverified placeholders. Replace them with the official BMC/MCGM list and set `floodProne` for low-lying sites.
- **Hospital and helpline numbers** in the guides should be re-verified periodically.
- Live state (hazards, occupancy) is in memory and single-instance. It resets on restart.

---

## 📜 Acknowledgements
Road data © OpenStreetMap contributors (via OSMnx). Geocoding by Komoot Photon and Nominatim. Map tiles © CARTO. Optional live traffic by TomTom, AI by Google Gemini.
