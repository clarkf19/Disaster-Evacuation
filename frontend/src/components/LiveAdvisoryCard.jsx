import styles from './LiveAdvisoryCard.module.css';

const STATUS_CONFIG = {
  CLEAR:             { label: '✅ Live Route Clear',      theme: 'clear',    dot: '#34a853' },
  SLOW_TRAFFIC:      { label: '🟡 Slight Slowdown',       theme: 'slow',     dot: '#fbbc04' },
  MODERATE_TRAFFIC:  { label: '🟡 Moderate Traffic',      theme: 'moderate', dot: '#fbbc04' },
  HEAVY_CONGESTION:  { label: '🔴 Heavy Traffic Delay',   theme: 'heavy',    dot: '#ea4335' },
  DISASTER_BYPASS:   { label: '🔵 Live Disaster Bypass',  theme: 'bypass',   dot: '#1a73e8' },
  UNPASSABLE:        { label: '🚫 Route Blocked',         theme: 'heavy',    dot: '#ea4335' },
};

/**
 * LiveAdvisoryCard — shows the real-time traffic status, advisory message,
 * and key route metrics (live time, free-flow time, delay).
 */
export default function LiveAdvisoryCard({ result, hasDisaster = false }) {
  if (!result) return null;

  const effectiveStatus = hasDisaster && result.liveStatus === 'CLEAR'
    ? 'DISASTER_BYPASS'
    : result.liveStatus;

  const cfg = STATUS_CONFIG[effectiveStatus] || STATUS_CONFIG.CLEAR;

  return (
    <div className={`${styles.card} ${styles[cfg.theme]}`}>
      {/* Header */}
      <div className={styles.header}>
        <span className={styles.dot} style={{ backgroundColor: cfg.dot }} />
        <span className={styles.badge}>{cfg.label}</span>
        <span className={styles.live}>LIVE</span>
      </div>

      {/* Advisory message */}
      <p className={styles.message}>{result.advisoryMessage}</p>

      {/* Metrics grid */}
      <div className={styles.metrics}>
        <MetricRow label="Total Distance" value={`${result.distanceKm.toFixed(1)} km`} />
        <MetricRow label="Live Travel Time" value={`${result.liveMinutes} mins`} accent />
        {result.delayMinutes > 0 && (
          <MetricRow
            label="Traffic Delay"
            value={`+${result.delayMinutes} min${result.delayMinutes !== 1 ? 's' : ''}`}
            warn
          />
        )}
        <MetricRow label="Free-Flow Time" value={`${result.freeFlowMinutes} mins`} muted />
      </div>

      {/* Segment legend */}
      <div className={styles.legend}>
        <LegendItem color="#34a853" label="Clear" />
        <LegendItem color="#fbbc04" label="Slow" />
        <LegendItem color="#ea4335" label="Heavy" />
      </div>
    </div>
  );
}

function MetricRow({ label, value, accent, warn, muted }) {
  return (
    <div className={styles.row}>
      <span className={styles.rowLabel}>{label}</span>
      <strong
        className={styles.rowValue}
        style={{
          color: accent ? '#1a73e8' : warn ? '#f97316' : muted ? '#94a3b8' : '#1e293b'
        }}
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
