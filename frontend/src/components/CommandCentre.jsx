import { useEffect, useState } from 'react';
import * as API from '../services/backendApi';
import EvaluationPanel from './EvaluationPanel';
import styles from './CommandCentre.module.css';

/** Assumption controls; walk + train shares are percentages, the rest drive. */
function Assumptions({ params, onChange }) {
  if (!params) return null;
  const set = (key, value) => onChange({ ...params, [key]: value });
  const walk = Math.round(params.walkShare * 100);
  const transit = Math.round(params.transitShare * 100);
  return (
    <details className={styles.assumptions}>
      <summary>Assumptions — {walk}% walk · {transit}% train · {100 - walk - transit}% drive</summary>
      <label className={styles.sliderRow}>
        <span>🚶 Walk {walk}%</span>
        <input type="range" min="0" max="100" step="5" value={walk}
               onChange={e => {
                 const w = Number(e.target.value) / 100;
                 onChange({ ...params, walkShare: w, transitShare: Math.min(params.transitShare, 1 - w) });
               }} />
      </label>
      <label className={styles.sliderRow}>
        <span>🚆 Train {transit}%</span>
        <input type="range" min="0" max="100" step="5" value={transit}
               onChange={e => {
                 const t = Number(e.target.value) / 100;
                 onChange({ ...params, transitShare: t, walkShare: Math.min(params.walkShare, 1 - t) });
               }} />
      </label>
      <label className={styles.sliderRow}>
        <span>🚗 People per vehicle {params.personsPerVehicle}</span>
        <input type="range" min="1" max="40" step="1" value={params.personsPerVehicle}
               onChange={e => set('personsPerVehicle', Number(e.target.value))} />
      </label>
      <label className={styles.sliderRow}>
        <span>⏱ Evacuation window {params.evacuationWindowHours} h</span>
        <input type="range" min="0.5" max="12" step="0.5" value={params.evacuationWindowHours}
               onChange={e => set('evacuationWindowHours', Number(e.target.value))} />
      </label>
      <label className={styles.sliderRow}>
        <span>⛺ Shelter capacity ×{params.capacityScale}</span>
        <input type="range" min="0.25" max="3" step="0.25" value={params.capacityScale}
               onChange={e => set('capacityScale', Number(e.target.value))} />
      </label>
    </details>
  );
}

const MODE_ICON = { WALK: '🚶 Walk', TRANSIT: '🚆 Train', DRIVE: '🚗 Drive' };

function ModeTable({ naive, aware }) {
  const modes = Object.keys(aware.byMode || {});
  if (modes.length === 0) return null;
  return (
    <table className={styles.table}>
      <thead>
        <tr><th>Mode</th><th>People</th><th>Avg min (naive)</th><th>Avg min (aware)</th></tr>
      </thead>
      <tbody>
        {modes.map(m => (
          <tr key={m}>
            <td>{MODE_ICON[m] || m}</td>
            <td>{aware.byMode[m].persons.toLocaleString()}</td>
            <td>{naive.byMode?.[m]?.avgMinutes ?? '—'}</td>
            <td>{aware.byMode[m].avgMinutes}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * Command Centre — runs sandboxed evacuation simulations (naive nearest-shelter
 * vs capacity-aware assignment) and the Dijkstra vs A* benchmark. Simulations
 * never change the live hazards or shelter occupancy other users see.
 */
export default function CommandCentre({ simulation, onSimulationChange }) {
  const [scenarios, setScenarios] = useState([]);
  const [scenarioId, setScenarioId] = useState('');
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const [benchmark, setBenchmark] = useState(null);
  const [benchRunning, setBenchRunning] = useState(false);
  const [params, setParams] = useState(simulation?.comparison?.scenario?.params || null);

  const initialScenarioId = simulation?.comparison?.scenario?.id;
  useEffect(() => {
    API.getPresetScenarios()
      .then(list => {
        setScenarios(list);
        setScenarioId(prev => prev || initialScenarioId || list[0]?.id || '');
      })
      .catch(e => setError(e.message));
    API.getSimulationDefaults()
      .then(d => setParams(prev => prev || d))
      .catch(() => {});
  }, [initialScenarioId]);

  async function runComparison() {
    setRunning(true);
    setError('');
    try {
      const comparison = await API.compareStrategies(scenarioId, params || {});
      onSimulationChange({ comparison, strategy: simulation?.strategy || 'capacityAware' });
    } catch (e) {
      setError(e.message);
    } finally {
      setRunning(false);
    }
  }

  async function runBenchmark() {
    setBenchRunning(true);
    setError('');
    try {
      setBenchmark(await API.benchmarkAlgorithms());
    } catch (e) {
      setError(e.message);
    } finally {
      setBenchRunning(false);
    }
  }

  const comparison = simulation?.comparison;
  const selected = scenarios.find(s => s.id === scenarioId);

  return (
    <div className={styles.panel}>
      <section className={styles.section}>
        <h2>Evacuation Simulation</h2>
        <p className={styles.hint}>
          Compare a naive “everyone to the nearest shelter” plan with capacity-aware assignment on the real road graph.
          Simulations are private sandboxes — they don't affect live hazards or shelters.
        </p>

        <label className={styles.label} htmlFor="scenario">Scenario</label>
        <select id="scenario" className={styles.select} value={scenarioId} onChange={e => setScenarioId(e.target.value)}>
          {scenarios.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        {selected && (
          <p className={styles.scenarioMeta}>
            {selected.description} <b>{selected.totalEvacuees.toLocaleString()}</b> evacuees in {selected.groupCount} groups.
          </p>
        )}

        <Assumptions params={params} onChange={setParams} />

        <button className={styles.btnPrimary} onClick={runComparison} disabled={running || !scenarioId}>
          {running ? 'Running simulation…' : '▶ Run Both Strategies'}
        </button>
        {error && <p className={styles.error} role="alert">{error}</p>}
      </section>

      {comparison && (
        <section className={styles.section}>
          <div className={styles.rowBetween}>
            <h3>Results — {comparison.scenario.name}</h3>
            {simulation && (
              <button className={styles.btnGhost} onClick={() => onSimulationChange(null)}>Clear map</button>
            )}
          </div>

          <MetricsTable naive={comparison.naive} aware={comparison.capacityAware} />
          <ModeTable naive={comparison.naive} aware={comparison.capacityAware} />

          <div className={styles.toggle} role="radiogroup" aria-label="Strategy shown on map">
            {[['naive', 'Show naive on map'], ['capacityAware', 'Show capacity-aware on map']].map(([key, label]) => (
              <button
                key={key}
                role="radio"
                aria-checked={simulation.strategy === key}
                className={`${styles.toggleBtn} ${simulation.strategy === key ? styles.toggleActive : ''}`}
                onClick={() => onSimulationChange({ ...simulation, strategy: key })}
              >
                {label}
              </button>
            ))}
          </div>

          <GroupOutcomes metrics={simulation.strategy === 'naive' ? comparison.naive : comparison.capacityAware} />

          {comparison.scenario.unsafeShelterIds.length > 0 && (
            <p className={styles.note}>
              {comparison.scenario.unsafeShelterIds.length} shelter(s) excluded because they lie inside a hazard zone.
            </p>
          )}
          <p className={styles.note}>
            Assumptions: {Math.round(comparison.scenario.params.walkShare * 100)}% walk,{' '}
            {Math.round(comparison.scenario.params.transitShare * 100)}% train, rest drive
            ({comparison.scenario.params.personsPerVehicle} per vehicle) over {comparison.scenario.params.evacuationWindowHours} h.
            Dashed routes = people turned away at a full shelter. Hover routes for details.
            A single run can mislead — use the evaluation below for averages.
          </p>
        </section>
      )}

      <EvaluationPanel scenarioId={scenarioId} params={params} />

      <section className={styles.section}>
        <h2>Algorithm Benchmark</h2>
        <p className={styles.hint}>Dijkstra vs A* on three long corridors, under the current live hazards.</p>
        <button className={styles.btnGhost} onClick={runBenchmark} disabled={benchRunning}>
          {benchRunning ? 'Running…' : '⏱ Run Dijkstra vs A*'}
        </button>
        {benchmark && (
          <table className={styles.table}>
            <thead>
              <tr><th>Corridor</th><th>Dijkstra nodes</th><th>A* nodes</th><th>Saved</th><th>Same cost</th></tr>
            </thead>
            <tbody>
              {benchmark.benchmarkResults.map(r => (
                <tr key={r.corridorName}>
                  <td>{r.corridorName}<br /><small>{r.aStar.travelTimeMinutes} min · {r.aStar.totalDistanceKm} km</small></td>
                  <td>{r.dijkstra.nodesExplored.toLocaleString()}<br /><small>{r.dijkstra.executionTimeMs} ms</small></td>
                  <td>{r.aStar.nodesExplored.toLocaleString()}<br /><small>{r.aStar.executionTimeMs} ms</small></td>
                  <td>{r.searchSpaceReductionPercent}%</td>
                  <td>{r.costsMatch ? '✅' : '❌'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

const ROWS = [
  ['Evacuees housed', m => m.evacueesHoused, 'higher'],
  ['Turned away / stranded', m => m.overflowEvacuees, 'lower'],
  ['Avg travel time (min)', m => m.avgEvacuationTimeMinutes, 'lower'],
  ['Worst travel time (min)', m => m.maxEvacuationTimeMinutes, 'lower'],
  ['Avg distance (km)', m => m.avgTravelDistanceKm, 'lower'],
  ['Shelters over capacity', m => m.sheltersOverCapacity, 'lower'],
  ['Congested road (km)', m => m.congestedRoadKm, 'lower'],
  ['Crowded rail (km)', m => m.crowdedRailKm, 'lower'],
  ['Re-routed groups', m => m.reroutedAllocations, null],
  ['Compute time (ms)', m => m.executionTimeMs, null],
];

function MetricsTable({ naive, aware }) {
  return (
    <table className={styles.table}>
      <thead>
        <tr><th>Metric</th><th>Naive</th><th>Capacity-aware</th></tr>
      </thead>
      <tbody>
        {ROWS.map(([label, get, better]) => {
          const a = get(naive), b = get(aware);
          const awareWins = better && a !== b && (better === 'higher' ? b > a : b < a);
          const naiveWins = better && a !== b && !awareWins;
          return (
            <tr key={label}>
              <td>{label}</td>
              <td className={naiveWins ? styles.better : ''}>{a.toLocaleString()}</td>
              <td className={awareWins ? styles.better : ''}>{b.toLocaleString()}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

const STATUS_STYLE = {
  EVACUATED: { label: 'Evacuated', color: '#16a34a' },
  PARTIAL: { label: 'Partly housed', color: '#c2410c' },
  OVERFLOW: { label: 'No space', color: '#dc2626' },
  UNREACHABLE: { label: 'Unreachable', color: '#dc2626' },
};

function GroupOutcomes({ metrics }) {
  return (
    <ul className={styles.groups}>
      {metrics.groups.map(g => {
        const st = STATUS_STYLE[g.status] || { label: g.status, color: '#64748b' };
        const destinations = metrics.allocations.filter(a => a.groupId === g.id);
        return (
          <li key={g.id} className={styles.groupItem}>
            <div className={styles.rowBetween}>
              <b>{g.name}</b>
              <span className={styles.status} style={{ color: st.color, borderColor: st.color }}>{st.label}</span>
            </div>
            <span className={styles.groupMeta}>
              {g.housed.toLocaleString()} / {g.count.toLocaleString()} housed
              {destinations.length > 0 && ` → ${destinations.map(a => `${a.shelterName} (${a.persons.toLocaleString()})`).join(', ')}`}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
