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

const shelterIconCache = new Map();
function shelterIcon(pct, unsafe) {
  const key = unsafe ? 'unsafe' : String(pct);
  if (!shelterIconCache.has(key)) {
    const color = unsafe ? '#94a3b8' : pct >= 90 ? '#ea4335' : pct >= 70 ? '#f97316' : '#34a853';
    const label = unsafe ? '⚠️' : `${pct}%`;
    shelterIconCache.set(key, L.divIcon({
      className: '',
      html: `
        <div style="width:34px;height:34px;background:white;border:3px solid ${color};border-radius:50%;
                    display:flex;align-items:center;justify-content:center;font-size:15px;
                    box-shadow:0 2px 8px rgba(0,0,0,0.2);position:relative;${unsafe ? 'opacity:0.7;' : ''}">
          ⛺
          <div style="position:absolute;bottom:-9px;left:50%;transform:translateX(-50%);background:${color};
                      color:white;font-size:9px;font-weight:800;padding:1px 4px;border-radius:4px;white-space:nowrap">${label}</div>
        </div>`,
      iconSize: [34, 34],
      iconAnchor: [17, 17],
    }));
  }
  return shelterIconCache.get(key);
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
  onSelectShelter,
}) {
  // Keep the map near the mapped road network (with generous padding).
  const maxBounds = useMemo(() => {
    if (!coverageBounds) return undefined;
    const [minLat, minLon, maxLat, maxLon] = coverageBounds;
    return L.latLngBounds([minLat - 0.25, minLon - 0.25], [maxLat + 0.25, maxLon + 0.25]);
  }, [coverageBounds]);

  return (
    <MapContainer
      center={[19.08, 72.88]}
      zoom={11}
      style={{ width: '100%', height: '100%' }}
      zoomControl
      minZoom={9}
      maxBounds={maxBounds}
      maxBoundsViscosity={0.8}
    >
      <TileLayer
        url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
        subdomains="abcd"
        maxZoom={19}
      />

      <MapClickHandler clickMode={clickMode} onMapClick={onMapClick} />
      <BoundsController routeResult={routeResult} simulation={simulation} />

      {/* ── Route segments, coloured by traffic / hazard factor ── */}
      {routeResult?.segments?.map((seg, i) => (
        <Polyline
          key={`seg-${i}`}
          positions={seg.points}
          pathOptions={{
            color:   CONGESTION_COLORS[seg.congestion] || '#1a73e8',
            weight:  CONGESTION_WEIGHT[seg.congestion] || 6,
            opacity: 0.92,
            lineCap: 'round',
            lineJoin: 'round',
          }}
        />
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
        return (
          <Marker key={s.id} position={[s.lat, s.lon]} icon={shelterIcon(pct, s.unsafe)}>
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
          </Marker>
        );
      })}

      {disasters.map(d => (
        <Fragment key={d.id}>
          <HazardZone d={d} />
        </Fragment>
      ))}

      <SimulationLayer simulation={simulation} />
    </MapContainer>
  );
}
