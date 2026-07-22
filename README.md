# Disaster Evacuation Route Planning & Optimization for Mumbai

A capacity-aware disaster evacuation system for the Mumbai Metropolitan Region (MMR). It processes real road graph data from OpenStreetMap, models dynamic disaster events (floods, fires, bridge collapses, chemical leaks) that block roads or multiply congestion, recalculates routes dynamically, and assigns evacuees to shelters with interactive Leaflet.js map visualization.

---

## System Architecture & Tech Stack

- **Data Processing & Road Extraction**: Python 3, OSMnx, NetworkX, Pandas (Filter: Motorway, Trunk, Primary, Secondary, Tertiary arterial roads).
- **Backend & Graph Engine**: Java 17+, Spring Boot 3, Custom In-Memory Adjacency List Graph Engine.
- **Algorithms**: Classical Dijkstra & A* with admissible Haversine travel time heuristic.
- **Database / Schema**: PostgreSQL-compatible schema (`schema.sql`).
- **Frontend**: HTML5, Vanilla CSS (Dark Glassmorphism UI), JavaScript (ES6+), Leaflet.js.

---

## Key Features

1. **Real OpenStreetMap Mumbai Road Graph**: Over 8,800 nodes and 17,000 road edges covering major arterial corridors (Western Express Highway, Eastern Express Highway, SV Road, LBS Marg, Coastal Road).
2. **Dynamic Travel Time Cost Model**:
   $$\text{Travel Time} = \frac{\text{Distance}}{\text{Speed Limit}} \times \text{Congestion Factor}$$
   Congestion ratio mapping: 0–30% $\rightarrow$ 1.0, 30–60% $\rightarrow$ 1.3, 60–80% $\rightarrow$ 1.7, >80% $\rightarrow$ 2.5.
3. **Dynamic Disaster Events**: Inject Floods, Fires, Bridge Collapses, and Chemical Leaks to dynamically block road edges or multiply traffic congestion (3.0x). Routes are recomputed from scratch in <25ms.
4. **Shelter Capacity Management**: 22 designated high-capacity evacuation shelters across MMR (Wankhede Stadium, BKC Grounds, Shivaji Park, etc.) with real-time occupancy tracking.
5. **Visually Stunning Dark Cartography**: Custom Esri Dark Canvas map tiles with neon glowing route lines and animated pulse markers.

---

## Project Structure

```
mumbai-evacuation-system/
├── data/
│   ├── mumbai_nodes.csv       # Extracted node dataset (8,851 nodes)
│   └── mumbai_edges.csv       # Extracted edge dataset (17,186 edges)
├── scripts/
│   └── extract_mumbai_graph.py# Python OSMnx road network extractor
├── backend/
│   ├── pom.xml                # Spring Boot 3 Maven configuration
│   └── src/main/java/com/mumbai/evacuation/
│       ├── algorithm/         # DijkstraEngine & AStarEngine
│       ├── disaster/          # DisasterEngine & DisasterEvent
│       ├── model/             # Node, Edge, Graph, Shelter, Heuristic
│       ├── service/           # GraphService & ShelterService
│       └── controller/        # RouteController, DisasterController, ShelterController
└── frontend/
    ├── index.html             # Dashboard UI
    ├── css/styles.css         # Glassmorphism dark design system
    └── js/                    # Leaflet map, API wrappers, simulation logic
```

---

## How to Run Locally

### 1. Extract / Generate Road Network CSVs (Optional)
```bash
python scripts/extract_mumbai_graph.py
```

### 2. Start the Backend Server (Port 8080)
```bash
cd backend
mvn spring-boot:run
```

### 3. Open the Frontend
Open `frontend/index.html` in any modern web browser.
