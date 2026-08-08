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
 * Reverse geocode coordinates to a precise locality name using Nominatim OSM.
 *
 * Nominatim returns detailed address fields (suburb, neighbourhood, road, etc.)
 * which let us display "Kalina, Santacruz East" instead of just "Mumbai".
 * Calls through the Vite proxy (/nominatim → nominatim.openstreetmap.org).
 */
export async function reverseGeocode(lat, lon) {
  try {
    let url = `/nominatim/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1&zoom=17`;
    let res = await fetch(url, { headers: { 'Accept': 'application/json' } }).catch(() => null);
    if (!res || !res.ok) {
      url = `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1&zoom=17`;
      res = await fetch(url, { headers: { 'Accept': 'application/json' } }).catch(() => null);
    }
    if (!res || !res.ok) return coordinateLabel(lat, lon);
    const data = await res.json();
    const addr = data.address || {};

    // Build a human-readable label from the most specific address parts available.
    // Priority: road > suburb/neighbourhood > city_district > city
    const specific = addr.road || addr.pedestrian || addr.suburb || addr.neighbourhood || addr.quarter;
    const area     = addr.suburb || addr.neighbourhood || addr.city_district || addr.county;
    const city     = addr.city || addr.town || 'Mumbai';

    if (specific && area && specific !== area) {
      return `${specific}, ${area}`;
    } else if (specific) {
      return `${specific}, ${city}`;
    } else if (area) {
      return `${area}, ${city}`;
    }
    return data.display_name?.split(',')[0]?.trim() || coordinateLabel(lat, lon);
  } catch (e) {
    console.warn('Reverse geocode error, returning raw coordinates:', e);
    return coordinateLabel(lat, lon);
  }
}


/**
 * Forward geocode autocomplete — returns rich suggestions like Google Maps.
 *
 * Uses Photon (photon.komoot.io) — an open-source geocoder built specifically
 * for autocomplete on OSM data. Unlike Nominatim (which only does exact-name
 * lookups), Photon returns POIs, roads, suburbs, railway stations, hospitals,
 * malls, etc. for any partial query — exactly what Google Maps autocomplete does.
 *
 * Photon response: GeoJSON FeatureCollection, each feature has:
 *   properties: { name, city, state, country, type, osm_type, osm_key, osm_value }
 *   geometry.coordinates: [lon, lat]
 *
 * We bias results towards Mumbai by passing lat/lon of Mumbai center.
 */
export async function searchPlaces(query) {
  if (!query || query.trim().length < 2) return [];

  // MMR geographic centre — Photon biases results toward this point
  // Placed at Andheri/JVLR crossover so both North (Virar) and South (Colaba) results rank well
  const MUMBAI_LAT = 19.18;
  const MUMBAI_LON = 72.93;

  // Full Mumbai Metropolitan Region bounding box: west, south, east, north
  // Covers: Colaba (south) → Virar/Vasai (north), Bandra (west) → Navi Mumbai/Panvel (east)
  const bbox = '72.70,18.84,73.10,19.52';

  let url = `/photon/api/?q=${encodeURIComponent(query.trim())}&lat=${MUMBAI_LAT}&lon=${MUMBAI_LON}&limit=15&lang=en&bbox=${bbox}`;

  try {
    let res = await fetch(url, { headers: { 'Accept': 'application/json' } }).catch(() => null);
    if (!res || !res.ok) {
      url = `https://photon.komoot.io/api/?q=${encodeURIComponent(query.trim())}&lat=${MUMBAI_LAT}&lon=${MUMBAI_LON}&limit=15&lang=en&bbox=${bbox}`;
      res = await fetch(url, { headers: { 'Accept': 'application/json' } }).catch(() => null);
    }
    if (!res || !res.ok) return [];

    const geojson = await res.json();
    const features = geojson.features || [];

    // Deduplicate by proximity (< 150m = same place shown twice)
    const seen = [];
    const unique = features.filter(f => {
      const [lon, lat] = f.geometry.coordinates;
      const dup = seen.some(s => Math.abs(s.lat - lat) < 0.0015 && Math.abs(s.lon - lon) < 0.0015);
      if (!dup) { seen.push({ lat, lon }); return true; }
      return false;
    });

    return unique.map(f => {
      const p = f.properties;
      const [lon, lat] = f.geometry.coordinates;

      // Primary name: Photon's `name` field is the place's own name (e.g. "Andheri Railway Station")
      const mainName = p.name || p.street || p.city || query;

      // Sub-text: locality > city > state, e.g. "Andheri West, Mumbai"
      const locality = p.locality || p.district || p.county;
      const city     = p.city || p.state;
      const subParts = [locality, city].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i);
      const subText  = subParts.join(', ') || 'Mumbai';

      // Build combined text for icon resolution
      const iconText = [mainName, p.osm_key, p.osm_value, p.type].filter(Boolean).join(' ');
      const icon = resolveIcon(iconText, p.osm_key, p.osm_value);

      return { name: mainName, subText, icon, lat, lon, type: p.type || p.osm_value || 'OSM' };
    }).filter(s => s.name && s.lat && s.lon);

  } catch (e) {
    console.warn('Place search error:', e);
    return [];
  }
}


/**
 * Resolve a category emoji icon from Photon OSM key/value tags and place name.
 * Photon's osm_key/osm_value (e.g. key="amenity" value="hospital") are more
 * precise than Nominatim's type strings — use them first, fall back to name keywords.
 */
function resolveIcon(text, osmKey, osmValue) {
  // Use Photon's structured OSM tags first (most reliable)
  if (osmKey === 'railway' || osmValue === 'station' || osmValue === 'halt' || osmValue === 'subway_entrance') return '🚇';
  if (osmKey === 'aeroway' || osmValue === 'aerodrome') return '✈️';
  if (osmValue === 'hospital' || osmValue === 'clinic' || osmValue === 'doctors') return '🏥';
  if (osmValue === 'school' || osmValue === 'college' || osmValue === 'university') return '🎓';
  if (osmValue === 'fuel') return '⛽';
  if (osmValue === 'police') return '👮';
  if (osmValue === 'fire_station') return '🚒';
  if (osmValue === 'restaurant' || osmValue === 'cafe' || osmValue === 'fast_food' || osmValue === 'food_court') return '🍽️';
  if (osmValue === 'hotel' || osmValue === 'hostel' || osmValue === 'guest_house') return '🏨';
  if (osmValue === 'supermarket' || osmValue === 'marketplace' || osmValue === 'mall') return '🛍️';
  if (osmValue === 'park' || osmValue === 'garden' || osmValue === 'recreation_ground') return '🌳';
  if (osmValue === 'stadium' || osmValue === 'sports_centre') return '🏟️';
  if (osmValue === 'place_of_worship' || osmValue === 'temple' || osmValue === 'mosque' || osmValue === 'church') return '🛕';
  if (osmValue === 'beach' || osmValue === 'coastline') return '🏖️';
  if (osmKey === 'highway') return '🛣️';
  if (osmKey === 'place') return '📍';

  // Fall back to name keyword matching
  const t = (text || '').toLowerCase();
  if (t.includes('hospital') || t.includes('clinic') || t.includes('medical')) return '🏥';
  if (t.includes('station') || t.includes('metro') || t.includes('railway') || t.includes('bus')) return '🚇';
  if (t.includes('airport')) return '✈️';
  if (t.includes('mall') || t.includes('market') || t.includes('bazaar')) return '🛍️';
  if (t.includes('school') || t.includes('college') || t.includes('university') || t.includes('iit') || t.includes('tiss')) return '🎓';
  if (t.includes('stadium') || t.includes('ground')) return '🏟️';
  if (t.includes('park') || t.includes('garden') || t.includes('maidan')) return '🌳';
  if (t.includes('hotel') || t.includes('lodge')) return '🏨';
  if (t.includes('temple') || t.includes('mosque') || t.includes('church') || t.includes('mandir')) return '🛕';
  if (t.includes('beach') || t.includes('sea face')) return '🏖️';
  return '📍';
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
