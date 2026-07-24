/**
 * TomTom Routing Service — calls the BACKEND proxy, not TomTom directly.
 *
 * The TomTom API key lives only in application.yml on the Spring Boot server.
 * The frontend never sees or handles the key. This is the correct architecture:
 *   Browser → POST /api/live-route → Backend → TomTom API (with secret key)
 */

const BACKEND = '/api';

/**
 * Calculate a live traffic-aware route via the backend proxy.
 * No API key in the frontend at all.
 */
export async function calcLiveRoute(fromLat, fromLon, toLat, toLon) {
  const res = await fetch(`${BACKEND}/live-route`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fromLat, fromLon, toLat, toLon }),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err?.message || `Route calculation failed (${res.status})`);
  }

  const data = await res.json();
  if (!data.pathFound) throw new Error('No route found between selected points.');
  return normalizeResponse(data);
}

/**
 * Reverse geocode coordinates to a place name via the backend proxy.
 */
export async function reverseGeocode(lat, lon) {
  try {
    const res = await fetch(`${BACKEND}/geocode?lat=${lat}&lon=${lon}`);
    if (!res.ok) return coordinateLabel(lat, lon);
    const data = await res.json();
    return data.name || coordinateLabel(lat, lon);
  } catch (_) {
    return coordinateLabel(lat, lon);
  }
}

function coordinateLabel(lat, lon) {
  return `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
}

/**
 * Normalize backend LiveRouteResponse into shape used by React components.
 * Backend uses camelCase matching the Java DTO field names.
 */
function normalizeResponse(data) {
  const status = data.liveStatus || 'CLEAR';

  // Build segment array for map polyline coloring
  const segments = (data.segments || []).map(seg => ({
    points: [[seg.startLat, seg.startLon], [seg.endLat, seg.endLon]],
    congestion: factorToCongestion(seg.congestionFactor),
  }));

  return {
    found: true,
    distanceKm:      data.distanceKm,
    liveMinutes:     data.liveTravelTimeMinutes,
    freeFlowMinutes: data.freeFlowTravelTimeMinutes,
    delayMinutes:    data.delayMinutes,
    liveStatus:      status,
    advisoryMessage: data.advisoryMessage,
    points:          data.routeCoordinates || [],
    segments,
  };
}

function factorToCongestion(factor) {
  if (factor >= 2.0) return 'heavy';
  if (factor >= 1.5) return 'moderate';
  if (factor >= 1.2) return 'slow';
  return 'clear';
}
