# Mumbai Disaster Evacuation System — Technical Architecture & Design Document

## 1. System Overview & Objectives
The **Mumbai Capacity-Aware Disaster Evacuation System** is a real-time disaster management and route optimization engine engineered for the Greater Mumbai Metropolitan Region (MMR). It models major arterial road corridors, simulates disaster events (floods, fires, bridge collapses, chemical leaks), mutates dynamic road edge capacities and congestion factors, and routes evacuee clusters to designated shelters while strictly enforcing shelter capacity limits.

---

## 2. Tech Stack & Architectural Decisions
- **Backend Core**: Java 17, Spring Boot 3.2.3 (In-Memory Adjacency List Graph Engine with ConcurrentHashMap structures for sub-25ms response times).
- **Data Extractor**: Python 3, OSMnx, NetworkX (OSMnx extraction filtered to `motorway`, `trunk`, `primary`, `secondary`, `tertiary`).
- **Frontend**: HTML5, Vanilla CSS3, JavaScript (ES6+), Leaflet.js (Map tile rendering & layer management).
- **Load Testing**: Apache JMeter (`scripts/mumbai_evacuation_jmeter_test.jmx`).

---

## 3. Data Model & Mathematical Formulations

### Road Edge Congestion Mapping Formula
Edge travel time ($T$) in seconds:
$$T = \begin{cases} \infty & \text{if road blocked by disaster} \\ \frac{\text{Distance (m)}}{\text{Speed Limit (m/s)}} \times \text{Congestion Factor} & \text{otherwise} \end{cases}$$

Where dynamic congestion factor ($C$) maps from volume-to-capacity ratio ($R = \frac{\text{Current Traffic}}{\text{Road Capacity}}$):
$$C = \begin{cases} 
1.0 & \text{for } 0.00 \le R \le 0.30 \\ 
1.3 & \text{for } 0.30 < R \le 0.60 \\ 
1.7 & \text{for } 0.60 < R \le 0.80 \\ 
2.5 & \text{for } R > 0.80 
\end{cases} \times \text{Disaster Congestion Multiplier}$$

---

## 4. Evacuation Assignment Strategies

### Strategy 1: Naive Nearest Baseline (Capacity-Blind)
- Assigns evacuee groups to their absolute nearest shelter by travel time.
- Does not track or enforce shelter capacity limits, leading to severe shelter overcrowding (overflow evacuees).

### Strategy 2: Capacity-Aware Greedy Assignment Engine
- Evaluates candidate shelters with remaining capacity ($C_{\text{rem}} \ge N_{\text{evacuees}}$).
- Assigns evacuee cluster to nearest available shelter using A* search.
- Reserves shelter capacity and increments traffic load along path edges.
- **Route Recalculation Trigger**: Automatically recomputes shortest path from scratch if road travel time increases by $\ge 20\%$ due to traffic buildup or road blockages.

---

## 5. Architectural Design Trade-off Justifications

1. **Why Full Recomputation over D* Lite?**
   - In-memory graph search across Mumbai's core arterial network executes in under $25\text{ ms}$. Full Dijkstra/A* recomputation guarantees global optimality, eliminates stale edge state bugs, and ensures explainable routing in real-time emergency ops.
2. **Why Major Arterials Only?**
   - Excluding minor residential roads and footpaths keeps the graph focused on primary evacuation corridors (WEH, EEH, Coastal Road, SV Road, LBS Marg) while dramatically accelerating pathfinding search space.
