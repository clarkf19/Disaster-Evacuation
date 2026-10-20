import { useState } from 'react';
import * as API from '../services/backendApi';
import DisasterProtectionHub from './DisasterProtectionHub';
import styles from './DisasterPanel.module.css';

const DISASTER_TYPES = [
  { value: 'FLOOD',           label: '🌊 Monsoon Flood' },
  { value: 'FIRE',            label: '🔥 Building Fire' },
  { value: 'BRIDGE_COLLAPSE', label: '🌉 Bridge / Road Damage' },
  { value: 'CHEMICAL_LEAK',   label: '☣️ Chemical Hazard' },
];

const MIN_RADIUS = 50;
const MAX_RADIUS = 10000;

/**
 * DisasterPanel — place/remove live hazard zones (operator mode only) and browse
 * protection guides, first aid and emergency hospital numbers.
 */
export default function DisasterPanel({ disasters, operatorMode, onLogin, runOperatorAction, onPlaceMode }) {
  const [type,   setType]   = useState('FLOOD');
  const [radius, setRadius] = useState(1000);
  const [action, setAction] = useState('block');
  const [loading, setLoading] = useState(false);
  const [panelView, setPanelView] = useState('both'); // 'both' | 'report' | 'protection'

  const radiusValid = Number.isFinite(radius) && radius >= MIN_RADIUS && radius <= MAX_RADIUS;

  async function run(apiCall, failureText) {
    setLoading(true);
    await runOperatorAction(apiCall, failureText);
    setLoading(false);
  }

  const handleRemove = id => run(() => API.removeDisaster(id), 'Could not remove the hazard');
  const handleClearAll = () => run(API.clearAllDisasters, 'Could not clear the hazards');
  const handleMonsoon = () => run(API.activateMonsoonHotspots, 'Could not flood the monsoon spots');

  return (
    <div className={styles.panelContainer}>
      {/* Top Mode Toggle */}
      <div className={styles.modeToggleBar}>
        <button
          className={`${styles.modeBtn} ${panelView === 'both' ? styles.activeModeBtn : ''}`}
          onClick={() => setPanelView('both')}
        >
          📊 Complete View
        </button>
        <button
          className={`${styles.modeBtn} ${panelView === 'report' ? styles.activeModeBtn : ''}`}
          onClick={() => setPanelView('report')}
        >
          📍 Report Event
        </button>
        <button
          className={`${styles.modeBtn} ${panelView === 'protection' ? styles.activeModeBtn : ''}`}
          onClick={() => setPanelView('protection')}
        >
          🛡️ Protection & Hospitals
        </button>
      </div>

      {(panelView === 'both' || panelView === 'report') && (
        <div className={styles.panel}>
          {/* Report Form */}
          <div className={styles.section}>
            <h3>Place Live Hazard Zone</h3>
            <p className={styles.hint}>
              Hazard zones affect routing for <b>everyone</b> using this server.
              To experiment privately, use the 📊 Command tab instead.
            </p>

            {!operatorMode && (
              <div className={styles.lockedCard}>
                <b>🔒 Operators only</b>
                <p>Placing a hazard changes routes for everyone using the site, so it needs operator login.</p>
                <button className={styles.btnUnlock} onClick={onLogin}>🔒 Operator login</button>
              </div>
            )}

            <label className={styles.label} htmlFor="hazard-type">Hazard Type</label>
            <select
              id="hazard-type"
              className={styles.select}
              value={type}
              onChange={e => setType(e.target.value)}
            >
              {DISASTER_TYPES.map(t => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>

            <label className={styles.label} htmlFor="hazard-radius">Impact Radius (meters, {MIN_RADIUS}–{MAX_RADIUS})</label>
            <input
              id="hazard-radius"
              className={styles.input}
              type="number"
              min={MIN_RADIUS} max={MAX_RADIUS} step={50}
              value={Number.isFinite(radius) ? radius : ''}
              onChange={e => setRadius(parseInt(e.target.value, 10))}
              aria-invalid={!radiusValid}
            />

            <label className={styles.label} htmlFor="hazard-impact">Road Impact</label>
            <select id="hazard-impact" className={styles.select} value={action} onChange={e => setAction(e.target.value)}>
              <option value="block">Block roads (people inside can still leave)</option>
              <option value="congest">Heavy congestion (3.5× travel time)</option>
            </select>

            <button
              className={styles.btnDanger}
              onClick={() => onPlaceMode({ type, radius, action })}
              disabled={!radiusValid || !operatorMode}
            >
              📍 Click Map to Place Hazard
            </button>

            <button
              className={styles.btnSmall}
              onClick={handleMonsoon}
              disabled={loading || !operatorMode}
              title="Floods every chronic water-logging spot at once; nearby low-lying roads slow down"
            >
              💧 Simulate a heavy monsoon day (flood all chronic spots)
            </button>
          </div>

          {/* Active Disasters */}
          <div className={styles.section}>
            <div className={styles.row}>
              <h3>Active Hazards ({disasters.length})</h3>
              {disasters.length > 0 && (
                <button className={styles.btnSmall} onClick={handleClearAll} disabled={loading || !operatorMode}>
                  Clear All
                </button>
              )}
            </div>

            {disasters.length === 0 ? (
              <p className={styles.empty}>No active hazards. Map is clear.</p>
            ) : (
              disasters.map(d => (
                <div key={d.id} className={styles.disasterCard}>
                  <div className={styles.disasterHeader}>
                    <span>
                      {DISASTER_TYPES.find(t => t.value === d.type)?.label || d.type}
                    </span>
                    <button
                      className={styles.removeBtn}
                      disabled={!operatorMode}
                      title={operatorMode ? 'Remove this hazard' : 'Operator login needed'}
                      onClick={() => handleRemove(d.id)}
                      aria-label={`Remove ${d.type} hazard`}
                    >✕</button>
                  </div>
                  <p className={styles.disasterMeta}>
                    Radius: {Math.round(d.radiusMeters)}m · Roads: <b style={{ color: d.blockRoads ? '#ea4335' : '#c2410c' }}>
                      {d.blockRoads ? 'BLOCKED' : 'CONGESTED'}
                    </b>
                  </p>
                  {d.description && <p className={styles.disasterMeta}>{d.description}</p>}
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Disaster Protection & Hospitals Hub */}
      {(panelView === 'both' || panelView === 'protection') && (
        <div style={{ marginTop: panelView === 'both' ? '16px' : '0' }}>
          <DisasterProtectionHub selectedDisasterType={type} />
        </div>
      )}
    </div>
  );
}
