/**
 * API Service layer connecting Leaflet frontend to Spring Boot backend.
 * TomTom live routing is proxied through the backend — no API key in frontend code.
 */
const API_BASE = 'http://localhost:8080/api';

const API = {
    // ---- Route & Graph Endpoints ----
    async getGraphStats() {
        const res = await fetch(`${API_BASE}/graph/stats`);
        return res.json();
    },

    async getAllNodes() {
        const res = await fetch(`${API_BASE}/nodes`);
        return res.json();
    },

    async getNearestNode(lat, lon) {
        const res = await fetch(`${API_BASE}/nearest?lat=${lat}&lon=${lon}`);
        return res.json();
    },

    async computeRoute(sourceNodeId, targetNodeId, algorithm = 'ASTAR') {
        const res = await fetch(`${API_BASE}/route`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sourceNodeId, targetNodeId, algorithm })
        });
        return res.json();
    },

    /**
     * Compute live route via the backend proxy (POST /api/live-route).
     * The backend calls TomTom with the API key stored in application.yml.
     * No API key is present in this frontend code.
     */
    async computeTomTomRoute(fromLat, fromLon, toLat, toLon) {
        const res = await fetch(`${API_BASE}/live-route`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fromLat, fromLon, toLat, toLon })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err?.message || 'Live routing request failed: ' + res.status);
        }

        const data = await res.json();
        if (!data.pathFound) throw new Error('No route found between selected points.');

        // Normalize backend LiveRouteResponse to frontend shape
        const segments = (data.segments || []).map(seg => ({
            startLat: seg.startLat, startLon: seg.startLon,
            endLat: seg.endLat, endLon: seg.endLon,
            congestionFactor: seg.congestionFactor, roadType: 'traffic'
        }));

        return {
            pathFound: true,
            totalDistanceKm: data.distanceKm,
            totalTravelTimeMinutes: data.liveTravelTimeMinutes,
            freeFlowTravelTimeMinutes: data.freeFlowTravelTimeMinutes,
            congestionDelayMinutes: data.delayMinutes,
            liveRouteStatus: data.liveStatus,
            liveAdvisoryMessage: data.advisoryMessage,
            rawCoordinates: data.routeCoordinates || [],
            segmentDetails: segments
        };
    },

    // ---- Disaster Endpoints ----
    async addDisaster(disasterData) {
        const res = await fetch(`${API_BASE}/disasters`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(disasterData)
        });
        return res.json();
    },

    async removeDisaster(id) {
        const res = await fetch(`${API_BASE}/disasters/${id}`, { method: 'DELETE' });
        return res.json();
    },

    async clearAllDisasters() {
        const res = await fetch(`${API_BASE}/disasters`, { method: 'DELETE' });
        return res.json();
    },

    async listDisasters() {
        const res = await fetch(`${API_BASE}/disasters`);
        return res.json();
    },

    // ---- Shelter Endpoints ----
    async getAllShelters() {
        const res = await fetch(`${API_BASE}/shelters`);
        return res.json();
    },

    async updateShelterCapacity(id, totalCapacity) {
        const res = await fetch(`${API_BASE}/shelters/${id}/capacity`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ totalCapacity })
        });
        return res.json();
    },

    async resetShelterOccupancies() {
        const res = await fetch(`${API_BASE}/shelters/reset`, { method: 'POST' });
        return res.json();
    },

    // ---- Evacuation Engine Endpoints ----
    async getPresetScenarios() {
        const res = await fetch(`${API_BASE}/evacuation/scenarios`);
        return res.json();
    },

    async loadPresetScenario(scenarioId) {
        const res = await fetch(`${API_BASE}/evacuation/scenarios/${scenarioId}/load`, { method: 'POST' });
        return res.json();
    },

    async getActiveEvacueeGroups() {
        const res = await fetch(`${API_BASE}/evacuation/groups`);
        return res.json();
    },

    async spawnEvacueeGroup(groupData) {
        const res = await fetch(`${API_BASE}/evacuation/groups`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(groupData)
        });
        return res.json();
    },

    async runEvacuationSimulation(strategy = 'CAPACITY_AWARE') {
        const res = await fetch(`${API_BASE}/evacuation/simulate?strategy=${strategy}`, { method: 'POST' });
        return res.json();
    },

    async compareEvacuationStrategies(scenarioName = 'Mumbai Scenario') {
        const res = await fetch(`${API_BASE}/evacuation/compare?scenarioName=${encodeURIComponent(scenarioName)}`, { method: 'POST' });
        return res.json();
    },

    // ---- Algorithm Benchmarks ----
    async getAlgorithmBenchmarks() {
        const res = await fetch(`${API_BASE}/benchmark/algorithms`);
        return res.json();
    }
};
