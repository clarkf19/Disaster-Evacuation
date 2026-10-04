/**
 * Route planning & geocoding helpers built on the backend API.
 *
 * The backend decides where a route comes from (TomTom live traffic with
 * hazard zones avoided, or our own hazard-aware road graph) and guarantees it
 * stays out of active hazard zones. This module only reshapes the response
 * for the UI.
 */
import { fetchLiveRoute, fetchPlaceName, fetchPlaceSuggestions } from './backendApi';

export async function calcLiveRoute(fromLat, fromLon, toLat, toLon) {
  return normalizeResponse(await fetchLiveRoute(fromLat, fromLon, toLat, toLon));
}

/** Human-readable place name for coordinates; falls back to the raw coordinates. */
export async function reverseGeocode(lat, lon) {
  try {
    const { name } = await fetchPlaceName(lat, lon);
    return name || coordinateLabel(lat, lon);
  } catch {
    return coordinateLabel(lat, lon);
  }
}

/** Autocomplete suggestions limited to the mapped area; empty list on failure. */
export async function searchPlaces(query) {
  if (!query || query.trim().length < 2) return [];
  try {
    return await fetchPlaceSuggestions(query.trim());
  } catch {
    return [];
  }
}

export function coordinateLabel(lat, lon) {
  return `${Number(lat).toFixed(4)}, ${Number(lon).toFixed(4)}`;
}

/** Shape the backend LiveRouteResponse for the map and advisory card. */
export function normalizeResponse(data) {
  const segments = (data.segments || [])
    .filter(seg => Array.isArray(seg.points) && seg.points.length > 1)
    .map(seg => ({ points: seg.points, congestion: factorToCongestion(seg.congestionFactor) }));

  return {
    found:           Boolean(data.pathFound),
    source:          data.routeSource || 'ROAD_GRAPH',
    distanceKm:      data.distanceKm ?? 0,
    liveMinutes:     data.liveTravelTimeMinutes ?? 0,
    freeFlowMinutes: data.freeFlowTravelTimeMinutes ?? 0,
    delayMinutes:    data.delayMinutes ?? 0,
    liveStatus:      data.liveStatus || 'CLEAR',
    advisoryMessage: data.advisoryMessage || '',
    warnings:        data.warnings || [],
    points:          data.routeCoordinates || [],
    segments,
  };
}

export function factorToCongestion(factor) {
  if (factor >= 2.0) return 'heavy';
  if (factor >= 1.5) return 'moderate';
  if (factor >= 1.2) return 'slow';
  return 'clear';
}
