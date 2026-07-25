/**
 * Backend API Service — connects to Spring Boot at localhost:8080.
 *
 * The Vite dev server proxies /api → http://localhost:8080/api,
 * so no CORS issues during development.
 */

const BASE = '/api';

async function json(url, opts = {}) {
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`API error ${res.status}: ${url}`);
  return res.json();
}

// --- Shelters ---
export const getAllShelters = () => json(`${BASE}/shelters`);

// --- Disasters ---
export const listDisasters  = () => json(`${BASE}/disasters`);
export const addDisaster    = (data) => json(`${BASE}/disasters`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data)
});
export const removeDisaster    = (id) => json(`${BASE}/disasters/${id}`, { method: 'DELETE' });
export const clearAllDisasters = () => json(`${BASE}/disasters`, { method: 'DELETE' });

// --- Nearest node (for disaster anchoring) ---
export const getNearestNode = (lat, lon) => json(`${BASE}/nearest?lat=${lat}&lon=${lon}`);

// --- Evacuation ---
export const getPresetScenarios    = () => json(`${BASE}/evacuation/scenarios`);
export const loadPresetScenario    = (id) => json(`${BASE}/evacuation/scenarios/${id}/load`, { method: 'POST' });
export const runEvacuationSim      = (strategy = 'CAPACITY_AWARE') =>
  json(`${BASE}/evacuation/simulate?strategy=${strategy}`, { method: 'POST' });
export const getActiveEvacueeGroups = () => json(`${BASE}/evacuation/groups`);
export const compareStrategies      = (name) =>
  json(`${BASE}/evacuation/compare?scenarioName=${encodeURIComponent(name)}`, { method: 'POST' });

// --- Emergency Chatbot ---
export const sendChatMessage = (message, userLat = null, userLon = null) =>
  json(`${BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, userLat, userLon }),
  });
