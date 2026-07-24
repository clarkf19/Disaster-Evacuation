import { useEffect, useRef } from 'react';
import {
  MapContainer,
  TileLayer,
  useMapEvents,
  Marker,
  Circle,
  Polyline,
  Popup,
  useMap,
} from 'react-leaflet';
import L from 'leaflet';

// Fix Leaflet default icon path issue with Vite
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

/**
 * Congestion color mapping for traffic segments.
 * Colors mirror Google Maps traffic layer conventions.
 */
const CONGESTION_COLORS = {
  clear:    '#34a853',  // Google Green — free flow
  slow:     '#fbbc04',  // Google Yellow — slight slowdown
  moderate: '#f97316',  // Orange — moderate congestion
  heavy:    '#ea4335',  // Google Red — severe congestion
};

const CONGESTION_WEIGHT = {
  clear:    6,
  slow:     6,
  moderate: 7,
  heavy:    8,
};

/** Custom source/dest pin icons */
function makePinIcon(color) {
  return L.divIcon({
    className: '',
    html: `
      <div style="
        width: 22px; height: 22px;
        background: ${color};
        border: 3px solid white;
        border-radius: 50%;
        box-shadow: 0 3px 10px rgba(0,0,0,0.3);
      "></div>
    `,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

const sourceIcon = makePinIcon('#1a73e8');
const destIcon   = makePinIcon('#ea4335');

/** Shelter icon based on occupancy */
function makeShelterIcon(pct) {
  const color = pct >= 90 ? '#ea4335' : pct >= 70 ? '#f97316' : '#34a853';
  return L.divIcon({
    className: '',
    html: `
      <div style="
        width: 34px; height: 34px;
        background: white;
        border: 3px solid ${color};
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        font-size: 15px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.2);
        position: relative;
      ">
        ⛺
        <div style="
          position: absolute; bottom: -9px; left: 50%; transform: translateX(-50%);
          background: ${color}; color: white;
          font-size: 9px; font-weight: 800;
          padding: 1px 4px; border-radius: 4px;
          white-space: nowrap;
        ">${pct}%</div>
      </div>
    `,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  });
}

/** Disaster icon */
const disasterIcons = {
  FLOOD:          '🌊',
  FIRE:           '🔥',
  BRIDGE_COLLAPSE:'🌉',
  CHEMICAL_LEAK:  '☣️',
};

function makeDisasterIcon(type) {
  return L.divIcon({
    className: '',
    html: `
      <div style="
        font-size: 22px;
        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4));
        animation: shake 0.5s infinite alternate;
      ">${disasterIcons[type] || '⚠️'}</div>
      <style>
        @keyframes shake {
          from { transform: rotate(-5deg); }
          to   { transform: rotate(5deg); }
        }
      </style>
    `,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
}

/**
 * MapClickHandler — handles map clicks and forwards them to the parent
 * depending on the current click mode ('source', 'dest', 'disaster').
 */
function MapClickHandler({ clickMode, onMapClick }) {
  useMapEvents({
    click: (e) => {
      if (clickMode) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

/**
 * BoundsController — auto-fits the map to the route whenever it changes.
 */
function BoundsController({ routeResult }) {
  const map = useMap();
  useEffect(() => {
    if (routeResult?.points?.length > 1) {
      const bounds = L.latLngBounds(routeResult.points);
      map.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [routeResult, map]);
  return null;
}

/**
 * MapView — the main React-Leaflet map with:
 *  - CartoDB Voyager light tiles (Google Maps look)
 *  - Color-coded live traffic polyline segments
 *  - Source / destination pin markers
 *  - Shelter markers with occupancy %
 *  - Disaster circles with impact radius
 */
export default function MapView({
  clickMode,
  onMapClick,
  source,
  dest,
  routeResult,
  shelters,
  disasters,
}) {
  return (
    <MapContainer
      center={[19.0760, 72.8777]}
      zoom={11}
      style={{ width: '100%', height: '100%' }}
      zoomControl={true}
    >
      {/* CartoDB Voyager — clean, light, Google Maps feel */}
      <TileLayer
        url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
        subdomains="abcd"
        maxZoom={19}
      />

      {/* Interaction & bounds controllers */}
      <MapClickHandler clickMode={clickMode} onMapClick={onMapClick} />
      <BoundsController routeResult={routeResult} />

      {/* ── Live Traffic Route Segments ── */}
      {routeResult?.segments?.map((seg, i) => (
        <Polyline
          key={i}
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

      {/* Fallback: if no segment data, draw full route in blue */}
      {routeResult?.points?.length > 1 && !routeResult?.segments?.length && (
        <Polyline
          positions={routeResult.points}
          pathOptions={{ color: '#1a73e8', weight: 6, opacity: 0.9 }}
        />
      )}

      {/* ── Source Marker ── */}
      {source && (
        <Marker position={[source.lat, source.lon]} icon={sourceIcon}>
          <Popup>
            <div className="popup-title">📍 Start</div>
            <div className="popup-sub">{source.name}</div>
          </Popup>
        </Marker>
      )}

      {/* ── Destination Marker ── */}
      {dest && (
        <Marker position={[dest.lat, dest.lon]} icon={destIcon}>
          <Popup>
            <div className="popup-title">🏁 Destination</div>
            <div className="popup-sub">{dest.name}</div>
          </Popup>
        </Marker>
      )}

      {/* ── Shelter Markers ── */}
      {shelters.map(s => {
        const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
        return (
          <Marker key={s.id || s.name} position={[s.lat, s.lon]} icon={makeShelterIcon(pct)}>
            <Popup>
              <div className="popup-title">{s.name}</div>
              <div className="popup-sub">
                Capacity: <b>{s.currentOccupancy?.toLocaleString()} / {s.totalCapacity?.toLocaleString()}</b>
              </div>
              <div className="popup-sub">
                Available: <b>{s.remainingCapacity?.toLocaleString()}</b>
              </div>
            </Popup>
          </Marker>
        );
      })}

      {/* ── Disaster Circles ── */}
      {disasters.map(d => (
        <div key={d.id}>
          <Circle
            center={[d.lat, d.lon]}
            radius={d.radiusMeters}
            pathOptions={{
              color:       d.blockRoads ? '#ea4335' : '#fbbc04',
              fillColor:   d.blockRoads ? '#ea4335' : '#fbbc04',
              fillOpacity: 0.25,
              weight:      2.5,
            }}
          />
          <Marker position={[d.lat, d.lon]} icon={makeDisasterIcon(d.type)}>
            <Popup>
              <div className="popup-title">{disasterIcons[d.type] || '⚠️'} {d.type}</div>
              <div className="popup-sub">{d.description}</div>
              <div className="popup-sub">Impact Radius: {d.radiusMeters}m</div>
              <div className="popup-sub">
                Roads: <b style={{ color: d.blockRoads ? '#ea4335' : '#f97316' }}>
                  {d.blockRoads ? 'BLOCKED' : 'CONGESTED'}
                </b>
              </div>
            </Popup>
          </Marker>
        </div>
      ))}
    </MapContainer>
  );
}
