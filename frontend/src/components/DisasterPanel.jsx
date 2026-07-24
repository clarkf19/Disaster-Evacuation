import { useState } from 'react';
import * as API from '../services/backendApi';
import styles from './DisasterPanel.module.css';

const DISASTER_TYPES = [
  { value: 'FLOOD',           label: '🌊 Monsoon Flood' },
  { value: 'FIRE',            label: '🔥 Building Fire' },
  { value: 'BRIDGE_COLLAPSE', label: '🌉 Bridge / Road Damage' },
  { value: 'CHEMICAL_LEAK',   label: '☣️ Chemical Hazard' },
];

/**
 * DisasterPanel — lets the user inject disaster events onto the map.
 * Connects to the Spring Boot backend to persist disaster state.
 *
 * On disaster placement, the parent re-fetches disasters and passes them
 * to MapView, which draws impact circles and triggers route re-calculation.
 */
export default function DisasterPanel({ disasters, onDisastersChange, onPlaceMode }) {
  const [type,   setType]   = useState('FLOOD');
  const [radius, setRadius] = useState(1000);
  const [action, setAction] = useState('block');
  const [loading, setLoading] = useState(false);

  async function handleRemove(id) {
    try {
      await API.removeDisaster(id);
      onDisastersChange();
    } catch (e) {
      console.error('Remove disaster error:', e);
    }
  }

  async function handleClearAll() {
    setLoading(true);
    try {
      await API.clearAllDisasters();
      onDisastersChange();
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.panel}>
      <div className={styles.section}>
        <h3>Report Disaster Event</h3>
        <p className={styles.hint}>Configure below, then click the button to place on map.</p>

        <label className={styles.label}>Disaster Type</label>
        <select className={styles.select} value={type} onChange={e => setType(e.target.value)}>
          {DISASTER_TYPES.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>

        <label className={styles.label}>Impact Radius (meters)</label>
        <input
          className={styles.input}
          type="number"
          min={200} max={5000}
          value={radius}
          onChange={e => setRadius(parseInt(e.target.value))}
        />

        <label className={styles.label}>Road Impact</label>
        <select className={styles.select} value={action} onChange={e => setAction(e.target.value)}>
          <option value="block">Block Road Completely</option>
          <option value="congest">Cause Heavy Congestion</option>
        </select>

        <button
          className={styles.btnDanger}
          onClick={() => onPlaceMode({ type, radius, action })}
        >
          📍 Click Map to Place Disaster
        </button>
      </div>

      {/* Active Disasters */}
      <div className={styles.section}>
        <div className={styles.row}>
          <h3>Active Disasters ({disasters.length})</h3>
          {disasters.length > 0 && (
            <button className={styles.btnSmall} onClick={handleClearAll} disabled={loading}>
              Clear All
            </button>
          )}
        </div>

        {disasters.length === 0 ? (
          <p className={styles.empty}>No active disasters. Map is clear.</p>
        ) : (
          disasters.map(d => (
            <div key={d.id} className={styles.disasterCard}>
              <div className={styles.disasterHeader}>
                <span>
                  {DISASTER_TYPES.find(t => t.value === d.type)?.label || d.type}
                </span>
                <button
                  className={styles.removeBtn}
                  onClick={() => handleRemove(d.id)}
                >✕</button>
              </div>
              <p className={styles.disasterMeta}>
                Radius: {d.radiusMeters}m · Roads: <b style={{ color: d.blockRoads ? '#ea4335' : '#f97316' }}>
                  {d.blockRoads ? 'BLOCKED' : 'CONGESTED'}
                </b>
              </p>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
