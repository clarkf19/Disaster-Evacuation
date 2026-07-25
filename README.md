# 🌊 Mumbai Disaster Evacuation Route Planning & Optimization System

> **A capacity-aware, real-time disaster evacuation system tailored for the Mumbai Metropolitan Region (MMR).**  
> Computes optimal evacuation corridors on a real road graph (8,851 nodes & 17,186 edges), dynamically reacts to disaster events (floods, fires, chemical leaks, bridge collapses), routes evacuees away from hazard zones, assigns evacuees to emergency shelters respecting capacity constraints, and features a voice-enabled Emergency AI Safety Assistant.

---

## 🌟 Key Features

### 1. ⚡ Live Traffic-Aware Evacuation Routing
- Computes real-time evacuation paths using Dijkstra / A* algorithms over Greater Mumbai's road network.
- Integrates live traffic data via TomTom Routing API to bypass congested bottlenecks along Western Express Highway (WEH), Eastern Express Highway (EEH), SV Road, and LBS Marg.

### 2. 🚨 Dynamic Disaster Event Simulation
- **Simulates 4 Major Disaster Types**:
  - 🌊 **Monsoon Flooding**: Blocks low-lying subways (Sion Circle, Milan Subway, Andheri Subway) and Mithi River overflow zones.
  - 🔥 **Urban Building Fires**: Inflates congestion and blocks localized street segments.
  - ☣️ **Chemical / Gas Leaks**: Sets crosswind exclusion zones around industrial corridors (Chembur, Trombay, Mahul).
  - 🏗️ **Bridge / Structure Collapses**: Immediately severs critical arterial bridge connections.
- Dynamically recalculates routes in real-time when hazards are added or removed.

### 3. ⛺ Capacity-Aware Shelter Assignment
- Tracks live occupancy across **22 designated emergency shelters** in Mumbai (schools, sports complexes, hospital compounds).
- Employs greedy capacity-aware assignment to prevent shelter overcrowding and automatically redirects excess evacuees to the nearest open facility.

### 4. 🤖 Emergency AI Safety Assistant
- Powered by **Google Gemini API** (`gemini-2.0-flash`, `gemini-1.5-flash`).
- **🎤 Web Speech API Voice Input**: Hands-free voice recognition with live transcription.
- **Mumbai-Specific Safety Playbooks**: Provides immediate, practical, step-by-step guidance for floods, fires, chemical leaks, and structure collapses (e.g., vertical evacuation rules, electricity shutoff, avoiding invisible manholes, crosswind gas leak navigation, and 3-tap acoustic rubble signals).

### 5. 🗺️ Google Maps Style Place Autocomplete
- Integrated with **Photon (Komoot)** & **Nominatim (OpenStreetMap)** geocoders.
- Provides 2-line autocomplete dropdown cards with category icons (`🏥` Hospitals, `🚇` Stations, `🛍️` Malls, `🎓` Schools, `📍` Localities) and granular micro-locality reverse geocoding (e.g., *"Kalina, Santacruz East"*).

---

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 17, Spring Boot 3.2, Maven |
| **Frontend** | React 18, Vite, Leaflet.js, Vanilla CSS Modules |
| **Data Processing / Graph Engine** | Python 3, OSMnx, NetworkX, GeoPandas |
| **AI / LLM** | Google Gemini API (REST) |
| **Geocoding & Search** | Photon API, Nominatim OpenStreetMap API, TomTom Search API |

---

## 📂 Project Structure

```
Disaster-Evacuation/
├── backend/                               # Spring Boot Application
│   ├── src/main/java/com/mumbai/evacuation/
│   │   ├── controller/                    # REST Controllers (LiveRoute, Chatbot, Disasters, Shelters)
│   │   ├── disaster/                      # Disaster Event Models (Flood, Fire, Chemical, Collapse)
│   │   ├── dto/                           # Data Transfer Objects
│   │   ├── model/                         # Graph, Node, Edge, Shelter models
│   │   └── service/                       # GraphService, EmergencyChatbotService, TomTomService
│   └── src/main/resources/
│       ├── application.yml                # Backend Configuration
│       ├── mumbai_nodes.csv               # Road network nodes dataset
│       └── mumbai_edges.csv               # Road network edges dataset
├── frontend/                              # React + Vite Application
│   ├── src/
│   │   ├── components/                    # UI Components (RoutePlanner, EmergencyChatbot, MapView)
│   │   ├── services/                      # API Services (backendApi, tomtomApi)
│   │   ├── App.jsx                        # Main Application Container
│   │   └── index.css                      # Global Styles
│   └── vite.config.js                     # Vite proxy config (/api, /photon, /nominatim)
├── data/                                  # Raw CSV and Graph Datasets
├── .env.example                           # Environment configuration template
└── README.md                              # Documentation
```

---

## ⚙️ Getting Started

### Prerequisites
- **Java JDK 17+** (or JDK 26)
- **Node.js 18+** & npm
- **Maven** (mvnw wrapper included)

---

### 1. Environment Setup

1. Copy `.env.example` to create `.env` in the root directory:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and add your API keys:
   ```env
   # TomTom API Key (for traffic routing)
   TOMTOM_API_KEY=your_tomtom_api_key_here

   # Emergency AI Assistant configuration
   LLM_PROVIDER=gemini
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

---

### 2. Run the Backend (Spring Boot)

```bash
cd backend

# Set JAVA_HOME if needed (Windows PowerShell example)
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"

# Run Maven Spring Boot app
.\mvnw.cmd spring-boot:run
```
> 🚀 Backend starts on **`http://localhost:8080`**

---

### 3. Run the Frontend (React / Vite)

In a new terminal window:
```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start Vite dev server
npm run dev
```
> 🌐 Frontend starts on **`http://localhost:5173`** (or **`http://localhost:5174`**)

---

## 📡 API Endpoints Summary

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/live-route` | Calculate traffic-aware, disaster-bypassing evacuation route |
| `POST` | `/api/chat` | Query the Gemini-powered Emergency AI Safety Assistant |
| `GET` | `/api/shelters` | Fetch list of emergency shelters with live capacity data |
| `GET` | `/api/disasters` | Get currently active simulated disasters |
| `POST` | `/api/disasters` | Add a new disaster event (flood, fire, chemical, collapse) |
| `DELETE` | `/api/disasters/{id}` | Clear a specific disaster event |
| `GET` | `/api/nearest?lat={lat}&lon={lon}` | Find nearest road network node |

---

## 📞 Emergency Contacts Integrated in AI Assistant

- **BMC Disaster Management**: `1916` / `022-22694725`
- **Medical Emergency / Ambulance**: `108`
- **Police Helpline**: `100` / `112`
- **Fire Brigade**: `101`
- **NDRF Control Room**: `011-24363260`
- **Railway Emergency**: `1512`

---

## 📜 License & Acknowledgements

Developed for the **Mumbai Metropolitan Region (MMR) Disaster Evacuation Research Project**.  
- Road network data extracted via **OSMnx** & **OpenStreetMap**.  
- Search & Geocoding powered by **Komoot Photon** & **Nominatim**.  
- LLM Emergency Guidance powered by **Google Gemini API**.
