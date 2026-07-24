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
 */
export async function calcLiveRoute(fromLat, fromLon, toLat, toLon) {
  try {
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
    return normalizeResponse(data);
  } catch (e) {
    console.error('Error in calcLiveRoute:', e);
    throw e;
  }
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
  } catch (e) {
    console.warn('Reverse geocode error, returning raw coordinates:', e);
    return coordinateLabel(lat, lon);
  }
}

/**
 * Forward geocode — search for places by name near a bias point.
 * Returns up to 6 suggestions: [{ name, lat, lon, type }]
 */
export async function searchPlaces(query, biasLat = 19.076, biasLon = 72.8777) {
  if (!query || query.trim().length < 2) return [];
  try {
    const res = await fetch(
      `${BACKEND}/search?q=${encodeURIComponent(query)}&lat=${biasLat}&lon=${biasLon}`
    );
    if (!res.ok) return [];
    return await res.json();
  } catch (e) {
    console.warn('Place search error:', e);
    return [];
  }
}

function coordinateLabel(lat, lon) {
  return `${Number(lat).toFixed(4)}, ${Number(lon).toFixed(4)}`;
}

/**
 * Normalize backend LiveRouteResponse into shape used by React components.
 * Retains 100% of the turn-by-turn road geometry points per segment.
 */
function normalizeResponse(data) {
  const status = data.liveStatus || 'CLEAR';

  // Build segment array preserving full road turn coordinates (seg.points)
  const segments = (data.segments || []).map(seg => ({
    points: (seg.points && seg.points.length > 0)
      ? seg.points
      : [[seg.startLat, seg.startLon], [seg.endLat, seg.endLon]],
    congestion: factorToCongestion(seg.congestionFactor),
  }));

  // Ensure overall route points exist
  const points = (data.routeCoordinates && data.routeCoordinates.length > 0)
    ? data.routeCoordinates
    : [];

  return {
    found: data.pathFound ?? true,
    distanceKm:      data.distanceKm ?? 0,
    liveMinutes:     data.liveTravelTimeMinutes ?? 0,
    freeFlowMinutes: data.freeFlowTravelTimeMinutes ?? 0,
    delayMinutes:    data.delayMinutes ?? 0,
    liveStatus:      status,
    advisoryMessage: data.advisoryMessage || 'Route calculation complete.',
    points,
    segments,
  };
}

function factorToCongestion(factor) {
  if (factor >= 2.0) return 'heavy';
  if (factor >= 1.5) return 'moderate';
  if (factor >= 1.2) return 'slow';
  return 'clear';
}
