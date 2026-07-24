import { useState } from 'react';
import { calcLiveRoute, reverseGeocode } from '../services/tomtomApi';
import LiveAdvisoryCard from './LiveAdvisoryCard';
import styles from './RoutePlanner.module.css';

/**
 * RoutePlanner — sidebar panel for live traffic-aware point-to-point routing.
 *
 * Interaction model:
 *  1. User clicks "Set Start" → next map click sets source lat/lon
 *  2. User clicks "Set Destination" → next map click sets dest lat/lon
 *  3. "Calculate Live Route" triggers TomTom API → draws colored polyline on map
 *
 * The parent App passes clickMode state management so MapView knows what to do
 * when the user clicks the map.
 */
export default function RoutePlanner({
  clickMode,
  setClickMode,
  source,
  dest,
  onClear,
  onRouteResult,
  hasDisaster,
  routeResult,
}) {
  const [loading, setLoading] = useState(false);
  const [error, setError]   = useState('');

  async function handleCompute() {
    if (!source || !dest) {
      setError('Please select both a start and destination point on the map.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const result = await calcLiveRoute(source.lat, source.lon, dest.lat, dest.lon);
      onRouteResult(result);
    } catch (e) {
      setError(e.message || 'Failed to calculate route. Ensure the backend server is running.');
      onRouteResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h2>Live Route Planner</h2>
        <p className={styles.sub}>Click the map to set points, then calculate your live route.</p>
      </div>

      {/* Source Input */}
      <LocationRow
        label="Start Location"
        placeholder="Click map to set start..."
        value={source?.name}
        active={clickMode === 'source'}
        color="var(--google-blue)"
        icon="🔵"
        onClick={() => setClickMode(clickMode === 'source' ? null : 'source')}
      />

      {/* Destination Input */}
      <LocationRow
        label="Destination"
        placeholder="Click map to set destination..."
        value={dest?.name}
        active={clickMode === 'dest'}
        color="var(--google-red)"
        icon="🔴"
        onClick={() => setClickMode(clickMode === 'dest' ? null : 'dest')}
      />

      {/* Tip */}
      {(clickMode === 'source' || clickMode === 'dest') && (
        <div className={styles.tip}>
          <span>📍</span> Click anywhere on the map to set your {clickMode === 'source' ? 'start' : 'destination'}
        </div>
      )}

      {/* Error */}
      {error && <p className={styles.error}>{error}</p>}

      {/* Action buttons */}
      <div className={styles.buttons}>
        <button
          className={styles.btnPrimary}
          onClick={handleCompute}
          disabled={loading || !source || !dest}
        >
          {loading ? (
            <><span className={styles.spinner} /> Calculating Live Route...</>
          ) : (
            '⚡ Calculate Live Route'
          )}
        </button>

        {(source || dest || routeResult) && (
          <button className={styles.btnGhost} onClick={onClear}>
            Clear Route
          </button>
        )}
      </div>

      {/* Live Advisory Result */}
      {routeResult && !loading && (
        <LiveAdvisoryCard result={routeResult} hasDisaster={hasDisaster} />
      )}
    </div>
  );
}

function LocationRow({ label, placeholder, value, active, color, icon, onClick }) {
  return (
    <div className={styles.locationRow}>
      <label className={styles.locationLabel}>{label}</label>
      <button
        className={`${styles.locationBtn} ${active ? styles.locationBtnActive : ''}`}
        onClick={onClick}
        style={active ? { borderColor: color, boxShadow: `0 0 0 3px ${color}22` } : {}}
      >
        <span className={styles.locationIcon}>{icon}</span>
        <span className={styles.locationText} style={{ color: value ? '#1e293b' : '#94a3b8' }}>
          {value || placeholder}
        </span>
        {active && <span className={styles.pulsing} style={{ backgroundColor: color }} />}
      </button>
    </div>
  );
}
