import styles from './LiveAdvisoryCard.module.css';

const STATUS_CONFIG = {
  CLEAR:            { label: '✅ Route Clear',              theme: 'clear',    dot: '#34a853' },
  SLOW_TRAFFIC:     { label: '🟡 Slight Slowdown',          theme: 'slow',     dot: '#fbbc04' },
  MODERATE_TRAFFIC: { label: '🟡 Moderate Delay',           theme: 'moderate', dot: '#fbbc04' },
  HEAVY_CONGESTION: { label: '🔴 Heavy Delay',              theme: 'heavy',    dot: '#ea4335' },
  DISASTER_BYPASS:  { label: '🔵 Diverted Around Hazards',  theme: 'bypass',   dot: '#1a73e8' },
  HAZARD_EGRESS:    { label: '🟠 Leave the Hazard Zone',    theme: 'heavy',    dot: '#f97316' },
  UNPASSABLE:       { label: '🚫 No Passable Route',        theme: 'heavy',    dot: '#ea4335' },
  OUT_OF_COVERAGE:  { label: '🗺️ Outside Mapped Area',      theme: 'moderate', dot: '#94a3b8' },
};

const SOURCE_LABEL = {
  TOMTOM_LIVE: 'Live traffic · TomTom',
  ROAD_GRAPH: 'Hazard-aware road graph',
};

/** Route status, advisory, warnings and key metrics for the computed route. */
export default function LiveAdvisoryCard({ result }) {
  if (!result) return null;
  const cfg = STATUS_CONFIG[result.liveStatus] || STATUS_CONFIG.CLEAR;

  return (
    <div className={`${styles.card} ${styles[cfg.theme]}`} role="status">
      <div className={styles.header}>
        <span className={styles.dot} style={{ backgroundColor: cfg.dot }} />
        <span className={styles.badge}>{cfg.label}</span>
        <span className={styles.live}>{SOURCE_LABEL[result.source] || result.source}</span>
      </div>

      {result.advisoryMessage && <p className={styles.message}>{result.advisoryMessage}</p>}

      {result.warnings?.length > 0 && (
        <ul className={styles.warnings}>
          {result.warnings.map((w, i) => <li key={i}>⚠️ {w}</li>)}
        </ul>
      )}

      {result.found && (
        <>
          <div className={styles.metrics}>
            <MetricRow label="Total Distance" value={`${result.distanceKm.toFixed(1)} km`} />
            <MetricRow label="Estimated Travel Time" value={`${result.liveMinutes} min`} accent />
            {result.delayMinutes > 0 && (
              <MetricRow label={result.source === 'TOMTOM_LIVE' ? 'Traffic Delay' : 'Hazard Delay'}
                         value={`+${result.delayMinutes} min`} warn />
            )}
            <MetricRow label="Free-Flow Time" value={`${result.freeFlowMinutes} min`} muted />
          </div>

          <div className={styles.legend}>
            <LegendItem color="#34a853" label="Clear" />
            <LegendItem color="#fbbc04" label="Slow" />
            <LegendItem color="#f97316" label="Moderate" />
            <LegendItem color="#ea4335" label="Heavy / exiting zone" />
          </div>
        </>
      )}
    </div>
  );
}

function MetricRow({ label, value, accent, warn, muted }) {
  return (
    <div className={styles.row}>
      <span className={styles.rowLabel}>{label}</span>
      <strong
        className={styles.rowValue}
        style={{ color: accent ? '#1a73e8' : warn ? '#c2410c' : muted ? '#64748b' : '#1e293b' }}
      >
        {value}
      </strong>
    </div>
  );
}

function LegendItem({ color, label }) {
  return (
    <div className={styles.legendItem}>
      <span className={styles.legendDot} style={{ backgroundColor: color }} />
      <span>{label}</span>
    </div>
  );
}
