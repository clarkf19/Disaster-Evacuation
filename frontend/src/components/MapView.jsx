import { Fragment, useEffect, useMemo } from 'react';
import {
  MapContainer,
  TileLayer,
  useMapEvents,
  Marker,
  Circle,
  CircleMarker,
  Polyline,
  Popup,
  Tooltip,
  useMap,
} from 'react-leaflet';
import L from 'leaflet';

/** Traffic / hazard colours for route segments. */
const CONGESTION_COLORS = {
  clear:    '#34a853',
  slow:     '#fbbc04',
  moderate: '#f97316',
  heavy:    '#ea4335',
};

const CONGESTION_WEIGHT = { clear: 6, slow: 6, moderate: 7, heavy: 8 };

/** Distinct colours for simulated evacuation routes, one per destination shelter. */
const SIM_PALETTE = ['#1a73e8', '#9333ea', '#0d9488', '#db2777', '#ca8a04', '#4f46e5', '#059669', '#dc2626'];

function makePinIcon(color) {
  return L.divIcon({
    className: '',
    html: `<div style="width:22px;height:22px;background:${color};border:3px solid white;border-radius:50%;box-shadow:0 3px 10px rgba(0,0,0,0.3)"></div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

const sourceIcon = makePinIcon('#1a73e8');
const destIcon   = makePinIcon('#ea4335');

function shelterColor(pct, unsafe) {
  return unsafe ? '#94a3b8' : pct >= 90 ? '#ea4335' : pct >= 70 ? '#f97316' : '#34a853';
}

/** Marker radius grows with capacity (shelters range from ~150 to ~3,000 places). */
function shelterRadius(capacity) {
  return Math.max(5, Math.min(11, 4 + Math.sqrt(capacity) / 8));
}

/** Route line style by segment kind: driving solid, walking dotted, train purple dashed. */
function segmentStyle(seg) {
  if (seg.kind === 'rail') {
    return { color: '#7c3aed', weight: 6, opacity: 0.9, dashArray: '12 8' };
  }
  const color = CONGESTION_COLORS[seg.congestion] || '#1a73e8';
  if (seg.kind === 'walk') {
    return { color, weight: 5, opacity: 0.95, dashArray: '1 9', lineCap: 'round' };
  }
  return { color, weight: CONGESTION_WEIGHT[seg.congestion] || 6, opacity: 0.92, lineCap: 'round', lineJoin: 'round' };
}

const DISASTER_EMOJI = {
  FLOOD:           '🌊',
  FIRE:            '🔥',
  BRIDGE_COLLAPSE: '🌉',
  CHEMICAL_LEAK:   '☣️',
};

const disasterIconCache = new Map();
function disasterIcon(type) {
  if (!disasterIconCache.has(type)) {
    disasterIconCache.set(type, L.divIcon({
      className: 'disaster-marker-icon',
      html: `<div class="disaster-marker-shake">${DISASTER_EMOJI[type] || '⚠️'}</div>`,
      iconSize: [30, 30],
      iconAnchor: [15, 15],
    }));
  }
  return disasterIconCache.get(type);
}

function MapClickHandler({ clickMode, onMapClick }) {
  useMapEvents({
    click: (e) => {
      if (clickMode) onMapClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

/** Fits the map to the route, or to the simulation when one is shown. */
function BoundsController({ routeResult, simulation }) {
  const map = useMap();
  useEffect(() => {
    if (routeResult?.points?.length > 1) {
      map.fitBounds(L.latLngBounds(routeResult.points), { padding: [50, 50] });
    }
  }, [routeResult, map]);
  useEffect(() => {
    const pts = [];
    simulation?.comparison?.scenario?.groups?.forEach(g => pts.push([g.lat, g.lon]));
    currentStrategy(simulation)?.allocations?.forEach(a => a.route?.length && pts.push(a.route[a.route.length - 1]));
    if (pts.length > 1) map.fitBounds(L.latLngBounds(pts), { padding: [60, 60] });
  }, [simulation, map]);
  return null;
}

function currentStrategy(simulation) {
  if (!simulation?.comparison) return null;
  return simulation.strategy === 'naive' ? simulation.comparison.naive : simulation.comparison.capacityAware;
}

function HazardZone({ d, dashed = false }) {
  const color = d.blockRoads ? '#ea4335' : '#f59e0b';
  return (
    <>
      <Circle
        center={[d.lat, d.lon]}
        radius={d.radiusMeters}
        pathOptions={{ color, fillColor: color, fillOpacity: dashed ? 0.12 : 0.25, weight: 2.5, dashArray: dashed ? '6 6' : undefined }}
      />
      <Marker position={[d.lat, d.lon]} icon={disasterIcon(d.type)}>
        <Popup>
          <div className="popup-title">{DISASTER_EMOJI[d.type] || '⚠️'} {d.type.replace('_', ' ')}{dashed ? ' (simulated)' : ''}</div>
          {d.description && <div className="popup-sub">{d.description}</div>}
          <div className="popup-sub">Impact radius: {Math.round(d.radiusMeters)} m</div>
          <div className="popup-sub">
            Roads: <b style={{ color: d.blockRoads ? '#ea4335' : '#c2410c' }}>{d.blockRoads ? 'BLOCKED (exit allowed)' : 'CONGESTED'}</b>
          </div>
        </Popup>
      </Marker>
    </>
  );
}

function SimulationLayer({ simulation }) {
  const metrics = currentStrategy(simulation);
  const scenario = simulation?.comparison?.scenario;
  const shelterColors = useMemo(() => {
    const colors = new Map();
    metrics?.allocations?.forEach(a => {
      if (!colors.has(a.shelterId)) colors.set(a.shelterId, SIM_PALETTE[colors.size % SIM_PALETTE.length]);
    });
    return colors;
  }, [metrics]);

  if (!scenario || !metrics) return null;
  const outcomes = new Map(metrics.groups.map(g => [g.id, g]));

  return (
    <>
      {scenario.disasters.map(d => <HazardZone key={`sim-${d.id}`} d={d} dashed />)}
      {metrics.allocations.map((a, i) => (
        <Polyline
          key={`alloc-${i}`}
          positions={a.route}
          pathOptions={{ color: shelterColors.get(a.shelterId), weight: 5, opacity: 0.85, dashArray: a.housed < a.persons ? '8 6' : undefined }}
        >
          <Tooltip sticky>
            {a.groupName} → {a.shelterName}: {a.persons.toLocaleString()} people, {a.travelTimeMinutes} min
            {a.housed < a.persons ? ` — ${(a.persons - a.housed).toLocaleString()} turned away` : ''}
            {a.rerouted ? ' (re-routed)' : ''}
          </Tooltip>
        </Polyline>
      ))}
      {scenario.groups.map(g => {
        const o = outcomes.get(g.id);
        const color = !o ? '#64748b' : o.overflow === 0 ? '#16a34a' : o.housed > 0 ? '#f97316' : '#dc2626';
        return (
          <CircleMarker key={`grp-${g.id}`} center={[g.lat, g.lon]} radius={9}
                        pathOptions={{ color: '#fff', weight: 2, fillColor: color, fillOpacity: 1 }}>
            <Tooltip>
              {g.name}: {g.count.toLocaleString()} people{o ? ` — ${o.status.toLowerCase()}` : ''}
            </Tooltip>
          </CircleMarker>
        );
      })}
    </>
  );
}

function LayerToggles({ layers, onToggleLayer, hasHotspots, hasStations }) {
  if (!onToggleLayer || (!hasHotspots && !hasStations)) return null;
  return (
    <div className="map-layer-toggles" role="group" aria-label="Map layers">
      {hasHotspots && (
        <label>
          <input type="checkbox" checked={layers.hotspots} onChange={() => onToggleLayer('hotspots')} />
          💧 Monsoon flooding spots
        </label>
      )}
      {hasStations && (
        <label>
          <input type="checkbox" checked={layers.stations} onChange={() => onToggleLayer('stations')} />
          🚆 Suburban stations
        </label>
      )}
    </div>
  );
}

export default function MapView({
  clickMode,
  onMapClick,
  source,
  dest,
  routeResult,
  shelters,
  disasters,
  simulation,
  coverageBounds,
  hotspots = [],
  stations = [],
  layers = { hotspots: false, stations: false },
  onToggleLayer,
  onSelectShelter,
}) {
  // Keep the map near the mapped road network (with generous padding).
  const maxBounds = useMemo(() => {
    if (!coverageBounds) return undefined;
    const [minLat, minLon, maxLat, maxLon] = coverageBounds;
    return L.latLngBounds([minLat - 0.25, minLon - 0.25], [maxLat + 0.25, maxLon + 0.25]);
  }, [coverageBounds]);

  return (
    <>
    <LayerToggles layers={layers} onToggleLayer={onToggleLayer}
                  hasHotspots={hotspots.length > 0} hasStations={stations.length > 0} />
    <MapContainer
      center={[19.08, 72.88]}
      zoom={11}
      style={{ width: '100%', height: '100%' }}
      zoomControl
      minZoom={9}
      maxBounds={maxBounds}
      maxBoundsViscosity={0.8}
      preferCanvas
    >
      {/* Standard OpenStreetMap tiles: free, no API key (CARTO basemaps now require one). */}
      <TileLayer
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        maxZoom={19}
      />

      <MapClickHandler clickMode={clickMode} onMapClick={onMapClick} />
      <BoundsController routeResult={routeResult} simulation={simulation} />

      {/* ── Route segments, coloured by traffic / hazard factor ── */}
      {layers.hotspots && hotspots.map(h => (
        <Circle
          key={h.id}
          center={[h.lat, h.lon]}
          radius={h.radiusMeters}
          pathOptions={{ color: '#0284c7', fillColor: '#38bdf8', fillOpacity: 0.25, weight: 1.5, dashArray: '4 4' }}
        >
          <Tooltip>
            💧 {h.name}{h.elevationM != null ? ` · ${Math.round(h.elevationM)} m` : ''}<br />
            <small>Chronic monsoon water-logging spot (approximate)</small>
          </Tooltip>
        </Circle>
      ))}

      {layers.stations && stations.map(st => (
        <CircleMarker
          key={st.id}
          center={[st.lat, st.lon]}
          radius={5}
          pathOptions={{ color: '#ffffff', weight: 1.5, fillColor: st.open ? '#7c3aed' : '#94a3b8', fillOpacity: 1 }}
        >
          <Tooltip>
            🚆 {st.name}{st.open ? '' : ' — CLOSED (hazard)'}<br />
            <small>{st.lines.join(', ')}</small>
          </Tooltip>
        </CircleMarker>
      ))}

      {routeResult?.segments?.map((seg, i) => (
        <Polyline key={`seg-${i}`} positions={seg.points} pathOptions={segmentStyle(seg)} />
      ))}
      {routeResult?.points?.length > 1 && !routeResult?.segments?.length && (
        <Polyline positions={routeResult.points} pathOptions={{ color: '#1a73e8', weight: 6, opacity: 0.9 }} />
      )}

      {source && (
        <Marker position={[source.lat, source.lon]} icon={sourceIcon}>
          <Popup>
            <div className="popup-title">📍 Start</div>
            <div className="popup-sub">{source.name}</div>
          </Popup>
        </Marker>
      )}

      {dest && (
        <Marker position={[dest.lat, dest.lon]} icon={destIcon}>
          <Popup>
            <div className="popup-title">🏁 Destination</div>
            <div className="popup-sub">{dest.name}</div>
          </Popup>
        </Marker>
      )}

      {shelters.map(s => {
        const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
        const color = shelterColor(pct, s.unsafe);
        return (
          <CircleMarker
            key={s.id}
            center={[s.lat, s.lon]}
            radius={shelterRadius(s.totalCapacity)}
            pathOptions={{ color: '#ffffff', weight: 2, fillColor: color, fillOpacity: s.unsafe ? 0.5 : 0.95 }}
          >
            <Tooltip>{s.name} · {s.unsafe ? 'unsafe' : `${pct}% full`}</Tooltip>
            <Popup>
              <div className="popup-title">{s.name}</div>
              {s.unsafe && <div className="popup-sub" style={{ color: '#dc2626' }}><b>Unsafe — inside an active hazard zone</b></div>}
              <div className="popup-sub">
                Occupancy: <b>{s.currentOccupancy?.toLocaleString()} / {s.totalCapacity?.toLocaleString()}</b>
              </div>
              <div className="popup-sub">Available: <b>{s.remainingCapacity?.toLocaleString()}</b></div>
              {onSelectShelter && !s.unsafe && !s.isFull && (
                <button className="popup-action" onClick={() => onSelectShelter(s)}>📍 Evacuate Here</button>
              )}
            </Popup>
          </CircleMarker>
        );
      })}

      {disasters.map(d => (
        <Fragment key={d.id}>
          <HazardZone d={d} />
        </Fragment>
      ))}

      <SimulationLayer simulation={simulation} />
    </MapContainer>
    </>
  );
}
