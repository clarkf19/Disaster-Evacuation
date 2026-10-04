import { useState } from 'react';
import * as API from '../services/backendApi';
import styles from './CommandCentre.module.css';

const METRICS = [
  { key: 'housedPercent', label: 'Housed (%)', better: 'higher' },
  { key: 'avgEvacuationTimeMinutes', label: 'Avg travel time (min)', better: 'lower' },
  { key: 'maxEvacuationTimeMinutes', label: 'Worst travel time (min)', better: 'lower' },
  { key: 'overflowEvacuees', label: 'Turned away / stranded', better: 'lower' },
  { key: 'sheltersOverCapacity', label: 'Shelters over capacity', better: 'lower' },
  { key: 'congestedRoadKm', label: 'Congested road (km)', better: 'lower' },
  { key: 'crowdedRailKm', label: 'Crowded rail (km)', better: 'lower' },
];

const PARAMETERS = {
  PERSONS_PER_VEHICLE: { label: 'People per vehicle', values: [1, 2, 4, 8, 16, 30] },
  WINDOW_HOURS: { label: 'Evacuation window (h)', values: [1, 2, 3, 6, 12] },
  CAPACITY_SCALE: { label: 'Shelter capacity (×)', values: [0.5, 0.75, 1, 1.5, 2] },
  WALK_SHARE: { label: 'Share walking', values: [0, 0.25, 0.5, 0.75, 1] },
  TRANSIT_SHARE: { label: 'Share taking the train', values: [0, 0.2, 0.4, 0.6, 0.8] },
};

const COLORS = { NAIVE_NEAREST: '#ea580c', CAPACITY_AWARE: '#1a73e8' };
const NAMES = { NAIVE_NEAREST: 'Naive', CAPACITY_AWARE: 'Capacity-aware' };

/**
 * Statistical evaluation: repeated randomised runs (Monte Carlo) and one-at-a-time
 * sensitivity analysis. All randomness is seeded, so results are reproducible.
 */
export default function EvaluationPanel({ scenarioId, params }) {
  const [runs, setRuns] = useState(30);
  const [variation, setVariation] = useState(30);
  const [jitter, setJitter] = useState(500);
  const [mc, setMc] = useState(null);
  const [mcRunning, setMcRunning] = useState(false);

  const [parameter, setParameter] = useState('PERSONS_PER_VEHICLE');
  const [runsPerValue, setRunsPerValue] = useState(10);
  const [metric, setMetric] = useState('housedPercent');
  const [sens, setSens] = useState(null);
  const [sensRunning, setSensRunning] = useState(false);
  const [error, setError] = useState('');

  const randomisation = { sizeVariation: variation / 100, locationJitterMeters: jitter, seed: 42 };

  async function runMonteCarlo() {
    setMcRunning(true);
    setError('');
    try {
      setMc(await API.runMonteCarlo({ scenarioId, runs, ...randomisation, ...(params || {}) }));
    } catch (e) {
      setError(e.message);
    } finally {
      setMcRunning(false);
    }
  }

  async function runSensitivity() {
    setSensRunning(true);
    setError('');
    try {
      setSens(await API.runSensitivity({
        scenarioId, parameter, values: PARAMETERS[parameter].values, runsPerValue, ...randomisation,
      }));
    } catch (e) {
      setError(e.message);
    } finally {
      setSensRunning(false);
    }
  }

  return (
    <section className={styles.section}>
      <h2>Evaluation</h2>
      <p className={styles.hint}>
        Repeats the scenario many times with randomised group sizes and locations, running both strategies on each
        copy. Results show the mean ± 95% confidence interval. Seeded, so re-running gives the same numbers.
      </p>

      <div className={styles.inlineInputs}>
        <label>Runs<input type="number" min="2" max="100" value={runs} onChange={e => setRuns(Number(e.target.value))} /></label>
        <label>Size ±%<input type="number" min="0" max="90" value={variation} onChange={e => setVariation(Number(e.target.value))} /></label>
        <label>Move ≤ m<input type="number" min="0" max="3000" step="100" value={jitter} onChange={e => setJitter(Number(e.target.value))} /></label>
      </div>
      <button className={styles.btnPrimary} onClick={runMonteCarlo} disabled={mcRunning || !scenarioId}>
        {mcRunning ? `Running ${runs * 2} simulations…` : `🎲 Run ${runs} randomised runs`}
      </button>

      {mc && <MonteCarloTable result={mc} />}

      <h3 className={styles.subheading}>Sensitivity analysis</h3>
      <p className={styles.hint}>How do the results change when one assumption changes?</p>
      <div className={styles.inlineInputs}>
        <label>Vary
          <select value={parameter} onChange={e => setParameter(e.target.value)}>
            {Object.entries(PARAMETERS).map(([k, p]) => <option key={k} value={k}>{p.label}</option>)}
          </select>
        </label>
        <label>Runs each<input type="number" min="1" max="30" value={runsPerValue} onChange={e => setRunsPerValue(Number(e.target.value))} /></label>
      </div>
      <button className={styles.btnGhost} onClick={runSensitivity} disabled={sensRunning || !scenarioId}>
        {sensRunning ? 'Running sweep…' : `📈 Sweep ${PARAMETERS[parameter].label.toLowerCase()}`}
      </button>

      {sens && (
        <>
          <label className={styles.label} htmlFor="sens-metric">Chart metric</label>
          <select id="sens-metric" className={styles.select} value={metric} onChange={e => setMetric(e.target.value)}>
            {METRICS.map(m => <option key={m.key} value={m.key}>{m.label}</option>)}
          </select>
          <SensitivityChart result={sens} metric={metric} />
        </>
      )}

      {error && <p className={styles.error} role="alert">{error}</p>}
    </section>
  );
}

function fmt(stat) {
  return `${stat.mean.toLocaleString()} ± ${stat.ci95.toLocaleString()}`;
}

function MonteCarloTable({ result }) {
  const naive = result.metrics.NAIVE_NEAREST;
  const aware = result.metrics.CAPACITY_AWARE;
  return (
    <>
      <table className={styles.table}>
        <thead>
          <tr><th>Metric (mean ± 95% CI)</th><th>Naive</th><th>Capacity-aware</th></tr>
        </thead>
        <tbody>
          {METRICS.map(m => {
            const a = naive[m.key], b = aware[m.key];
            // Highlight only when the confidence intervals don't overlap.
            const separated = Math.abs(a.mean - b.mean) > a.ci95 + b.ci95;
            const awareWins = separated && (m.better === 'higher' ? b.mean > a.mean : b.mean < a.mean);
            const naiveWins = separated && !awareWins;
            return (
              <tr key={m.key}>
                <td>{m.label}<br /><small>sd {a.std} / {b.std}</small></td>
                <td className={naiveWins ? styles.better : ''}>{fmt(a)}</td>
                <td className={awareWins ? styles.better : ''}>{fmt(b)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className={styles.note}>
        Capacity-aware housed at least as many people as naive in <b>{result.capacityAwareWinRate}%</b> of {result.runs} runs.
        Bold = difference larger than both confidence intervals. Took {(result.executionTimeMs / 1000).toFixed(1)} s.
      </p>
      <button className={styles.btnGhost} onClick={() => downloadCsv(result)}>⬇ Download per-run CSV</button>
    </>
  );
}

function downloadCsv(result) {
  const header = 'run,strategy,housed_percent,avg_minutes,max_minutes,overflow,congested_road_km,crowded_rail_km';
  const lines = result.perRun.map(r =>
    [r.run, r.strategy, r.housedPercent, r.avgMinutes, r.maxMinutes, r.overflow, r.congestedRoadKm, r.crowdedRailKm].join(','));
  const blob = new Blob([[header, ...lines].join('\n')], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `evaluation-${result.scenarioId}-seed${result.seed}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

/** Line chart (inline SVG) of one metric vs. the swept parameter, with 95% CI error bars. */
function SensitivityChart({ result, metric }) {
  const W = 360, H = 200, pad = { l: 44, r: 12, t: 12, b: 34 };
  const points = result.points;
  const xs = points.map(p => p.value);
  const series = ['NAIVE_NEAREST', 'CAPACITY_AWARE'].map(s => ({
    strategy: s,
    values: points.map(p => p.metrics[s][metric]),
  }));
  const allY = series.flatMap(s => s.values.flatMap(v => [v.mean - v.ci95, v.mean + v.ci95]));
  let yMin = Math.min(...allY), yMax = Math.max(...allY);
  if (yMin === yMax) { yMin -= 1; yMax += 1; }
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const x = v => pad.l + (xMax === xMin ? 0.5 : (v - xMin) / (xMax - xMin)) * (W - pad.l - pad.r);
  const y = v => pad.t + (1 - (v - yMin) / (yMax - yMin)) * (H - pad.t - pad.b);
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map(f => yMin + f * (yMax - yMin));
  const label = METRICS.find(m => m.key === metric)?.label;

  return (
    <figure className={styles.chart}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${label} versus ${PARAMETERS[result.parameter].label}`}>
        {yTicks.map((t, i) => (
          <g key={i}>
            <line x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} stroke="#e2e8f0" />
            <text x={pad.l - 6} y={y(t) + 3} textAnchor="end" fontSize="9" fill="#64748b">{Math.round(t * 10) / 10}</text>
          </g>
        ))}
        {xs.map(v => (
          <text key={v} x={x(v)} y={H - pad.b + 14} textAnchor="middle" fontSize="9" fill="#64748b">{v}</text>
        ))}
        <text x={(pad.l + W - pad.r) / 2} y={H - 4} textAnchor="middle" fontSize="10" fill="#334155">
          {PARAMETERS[result.parameter].label}
        </text>
        {series.map(s => (
          <g key={s.strategy} stroke={COLORS[s.strategy]} fill={COLORS[s.strategy]}>
            <polyline fill="none" strokeWidth="2" points={s.values.map((v, i) => `${x(xs[i])},${y(v.mean)}`).join(' ')} />
            {s.values.map((v, i) => (
              <g key={i}>
                <line x1={x(xs[i])} x2={x(xs[i])} y1={y(v.mean - v.ci95)} y2={y(v.mean + v.ci95)} strokeWidth="1.5" />
                <circle cx={x(xs[i])} cy={y(v.mean)} r="3" />
              </g>
            ))}
          </g>
        ))}
      </svg>
      <figcaption>
        {label}: <span style={{ color: COLORS.NAIVE_NEAREST }}>■ {NAMES.NAIVE_NEAREST}</span>{' '}
        <span style={{ color: COLORS.CAPACITY_AWARE }}>■ {NAMES.CAPACITY_AWARE}</span> · {result.runsPerValue} runs per point,
        bars = 95% CI
      </figcaption>
    </figure>
  );
}
