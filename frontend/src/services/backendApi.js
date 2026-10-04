/**
 * Backend API client.
 *
 * All calls go to same-origin /api: the Vite dev server proxies it to
 * http://localhost:8080 and Vercel rewrites it to the Render backend, so no
 * third-party service or API key is ever called from the browser.
 */

const BASE = '/api';
const TOKEN_KEY = 'operatorToken';

/** Error carrying the HTTP status and the backend's message. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// ── Operator token (needed to change live disasters / shelters when the backend requires it) ──

export function getOperatorToken() {
  try { return sessionStorage.getItem(TOKEN_KEY) || ''; } catch { return ''; }
}

export function setOperatorToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable — token just won't persist */ }
}

async function request(path, { method = 'GET', body, operator = false } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (operator && getOperatorToken()) headers['X-Admin-Token'] = getOperatorToken();

  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection — the backend may also be waking up (allow ~1 minute).');
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const details = Array.isArray(err.details) ? ` (${err.details.join('; ')})` : '';
    throw new ApiError(res.status, (err.message || `Request failed (${res.status})`) + details);
  }
  return res.status === 204 ? null : res.json();
}

// --- App config & health ---
export const getConfig = () => request('/config');

// --- Shelters ---
export const getAllShelters = () => request('/shelters');

// --- Disasters (mutations need the operator token when the backend requires one) ---
export const listDisasters     = () => request('/disasters');
export const addDisaster       = (data) => request('/disasters', { method: 'POST', body: data, operator: true });
export const removeDisaster    = (id) => request(`/disasters/${encodeURIComponent(id)}`, { method: 'DELETE', operator: true });
export const clearAllDisasters = () => request('/disasters', { method: 'DELETE', operator: true });

// --- Evacuation simulation (sandboxed — never changes live state) ---
export const getPresetScenarios = () => request('/evacuation/scenarios');
export const compareStrategies  = (scenarioId) => request('/evacuation/compare', { method: 'POST', body: { scenarioId } });
export const benchmarkAlgorithms = () => request('/benchmark/algorithms');

// --- Emergency chatbot ---
export const sendChatMessage = (message, userLat = null, userLon = null) =>
  request('/chat', { method: 'POST', body: { message, userLat, userLon } });

// --- Disaster protection guides & hospitals ---
export const getDisasterProtectionGuides = () => request('/disaster-info/guides');
export const getEmergencyHospitals = (disasterType = '', region = '') => {
  const params = new URLSearchParams();
  if (disasterType) params.append('disasterType', disasterType);
  if (region) params.append('region', region);
  const q = params.toString();
  return request(`/disaster-info/hospitals${q ? `?${q}` : ''}`);
};

// --- Routing & geocoding (all proxied by the backend) ---
export const fetchLiveRoute = (fromLat, fromLon, toLat, toLon) =>
  request('/live-route', { method: 'POST', body: { fromLat, fromLon, toLat, toLon } });
export const fetchPlaceName = (lat, lon) => request(`/geocode?lat=${lat}&lon=${lon}`);
export const fetchPlaceSuggestions = (q) => request(`/search?q=${encodeURIComponent(q)}`);
