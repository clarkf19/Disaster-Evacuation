import { useState } from 'react';
import styles from './ShelterPanel.module.css';

const PAGE = 30;

function distanceKm(a, b) {
  const dLat = (b.lat - a.lat) * 111.32;
  const dLon = (b.lon - a.lon) * 111.32 * Math.cos(a.lat * Math.PI / 180);
  return Math.sqrt(dLat * dLat + dLon * dLon);
}

const fullness = s => (s.totalCapacity ? s.currentOccupancy / s.totalCapacity : 0);

export default function ShelterPanel({
  shelters, origin, dataVerified, demoMode, demo, hazardsActive, operatorMode, onLogin,
  onToggleDemo, onStartDrill, onStopDrill, destinationShelterId, onPlanRoute, onSelectShelter,
}) {
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(PAGE);
  const q = query.trim().toLowerCase();
  const sorted = shelters
    .filter(s => !q || s.name.toLowerCase().includes(q))
    .map(s => ({ ...s, km: origin ? distanceKm(origin, s) : null }))
    .sort((a, b) => (a.unsafe - b.unsafe)
      || (origin ? a.km - b.km : (fullness(b) - fullness(a)) || a.name.localeCompare(b.name)));
  const totalPeople = shelters.reduce((sum, s) => sum + (s.currentOccupancy || 0), 0);
  const arrivingNow = shelters.reduce((sum, s) => sum + Math.max(0, s.recentChange || 0), 0);

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h3>Evacuation Shelters</h3>
        <p className={styles.sub}>
          Capacity and occupancy for {shelters.length} shelter sites. Shelters inside an active hazard zone are marked unsafe.
        </p>
        {dataVerified === false && (
          <p className={styles.unverified}>
            ⚠️ Shelters are municipal schools from OpenStreetMap (BMC opens these during floods). Capacities are
            estimated from campus size — this is not the official BMC shelter list.
          </p>
        )}
      </div>

      {demoMode !== undefined && (
        <OccupancyCard
          demoMode={demoMode}
          demo={demo}
          hazardsActive={hazardsActive}
          totalPeople={totalPeople}
          arrivingNow={arrivingNow}
          operatorMode={operatorMode}
          onLogin={onLogin}
          onToggleDemo={onToggleDemo}
          onStartDrill={onStartDrill}
          onStopDrill={onStopDrill}
        />
      )}

      <div className={styles.searchRow}>
        <input
          className={styles.search}
          type="search"
          placeholder={`Search ${shelters.length} shelters…`}
          value={query}
          onChange={e => { setQuery(e.target.value); setLimit(PAGE); }}
          aria-label="Search shelters"
        />
        <span className={styles.sortHint}>{origin ? 'Nearest to your start first' : 'Fullest first'}</span>
      </div>

      <div className={styles.shelterList}>
        {shelters.length === 0 ? (
          <p className={styles.empty}>Loading shelters...</p>
        ) : (
          sorted.slice(0, limit).map((s) => {
            const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
            let barColor = '#34a853';
            if (s.unsafe) barColor = '#94a3b8';
            else if (pct >= 90) barColor = '#ea4335';
            else if (pct >= 70) barColor = '#f97316';

            return (
              <div key={s.id} className={`${styles.card} ${s.unsafe ? styles.cardUnsafe : ''}`}>
                <div className={styles.cardHeader}>
                  <span className={styles.name}>
                    {s.name}
                    {s.recentChange > 0 && <span className={styles.arriving}>+{s.recentChange.toLocaleString()} arriving</span>}
                    {s.recentChange < 0 && <span className={styles.leaving}>{s.recentChange.toLocaleString()} leaving</span>}
                  </span>
                  <span className={styles.badge} style={{ backgroundColor: `${barColor}15`, color: barColor }}>
                    {s.unsafe ? '⚠️ Unsafe' : `${pct}% Full`}
                  </span>
                </div>
                <div className={styles.capacityMeta}>
                  <span>
                    Occupancy: <b>{s.currentOccupancy?.toLocaleString()}</b> / {s.totalCapacity?.toLocaleString()}
                  </span>
                  <span>
                    Available: <b>{s.remainingCapacity?.toLocaleString()}</b>
                  </span>
                </div>
                <div className={styles.progressBg}>
                  <div className={styles.progressFill} style={{ width: `${pct}%`, backgroundColor: barColor }} />
                </div>
                <p className={styles.note}>
                  {s.km != null && `${s.km.toFixed(1)} km away · `}
                  {s.elevationM != null && `${Math.round(s.elevationM)} m elevation · `}
                  {s.kind === 'open_ground'
                    ? 'Open-air assembly ground — not used during floods'
                    : s.floodProne ? 'Low-lying / flood-prone — not used during floods' : 'Municipal school · capacity estimated'}
                </p>
                {onSelectShelter && (destinationShelterId === s.id ? (
                  <button className={`${styles.navBtn} ${styles.navBtnSet}`} onClick={onPlanRoute}>
                    ✓ Destination set — plan the route →
                  </button>
                ) : (
                  <button
                    className={styles.navBtn}
                    onClick={() => onSelectShelter(s)}
                    disabled={s.unsafe || s.isFull}
                  >
                    {s.unsafe ? 'Unavailable — inside hazard zone' : s.isFull ? 'Full' : '📍 Set as Evacuation Destination'}
                  </button>
                ))}
              </div>
            );
          })
        )}
        {sorted.length > limit && (
          <button className={styles.moreBtn} onClick={() => setLimit(l => l + PAGE)}>
            Show more ({sorted.length - limit} remaining)
          </button>
        )}
        {shelters.length > 0 && sorted.length === 0 && <p className={styles.empty}>No shelter matches “{query}”.</p>}
      </div>
    </div>
  );
}

/** Live occupancy explanation, counters and the operator's drill / demo controls. */
function OccupancyCard({
  demoMode, demo, hazardsActive, totalPeople, arrivingNow, operatorMode, onLogin,
  onToggleDemo, onStartDrill, onStopDrill,
}) {
  const [busy, setBusy] = useState(false);
  const phase = demo?.drill?.phase || 'NONE';
  const drilling = phase !== 'NONE';
  const act = fn => async () => { setBusy(true); await fn(); setBusy(false); };

  let title, text;
  if (!demoMode) {
    title = 'Demo mode is off';
    text = 'Occupancy changes only when operators update it.';
  } else if (phase === 'ARRIVING') {
    title = '🚨 Evacuation drill in progress';
    text = `${demo.drill.evacuees.toLocaleString()} residents from ${demo.drill.origins} monsoon flooding spots are heading to their nearest safe shelters${arrivingNow ? ` (+${arrivingNow.toLocaleString()} just arrived)` : ''}.`;
  } else if (phase === 'SHELTERED') {
    title = '🏁 Drill: everyone has arrived';
    text = 'Shelters stay occupied for about a minute and a half, then people go home.';
  } else if (hazardsActive) {
    title = '🎬 Simulated arrivals';
    text = `People from active hazard zones are heading to the nearest safe shelters${arrivingNow ? ` (+${arrivingNow.toLocaleString()} just arrived)` : ''}.`;
  } else if (totalPeople > 0) {
    title = '🎬 Simulated arrivals';
    text = 'No active hazards — shelters are emptying as people return home.';
  } else {
    title = '🎬 Simulated occupancy (demo)';
    text = 'Run an evacuation drill to watch shelters fill up, or place a hazard zone in the Hazards tab.';
  }

  return (
    <div className={`${styles.demoBanner} ${demoMode ? styles.demoOn : ''}`}>
      <div className={styles.demoText}>
        <b>{title}</b>
        <p>{text}</p>
        {demoMode && (totalPeople > 0 || drilling) && (
          <p className={styles.demoStats}>
            <span><b>{totalPeople.toLocaleString()}</b> sheltered</span>
            {demo?.waiting > 0 && <span><b>{demo.waiting.toLocaleString()}</b> on the way</span>}
            {demo?.unplaced > 0 && <span><b>{demo.unplaced.toLocaleString()}</b> turned away</span>}
          </p>
        )}
        <p className={styles.demoNote}>Simulated for demonstration — not real check-in data.</p>
      </div>
      <div className={styles.demoActions}>
        {operatorMode ? (
          <>
            {drilling
              ? <button className={styles.drillBtn} onClick={act(onStopDrill)} disabled={busy}>■ End drill</button>
              : <button className={styles.drillBtn} onClick={act(onStartDrill)} disabled={busy}>▶ Run evacuation drill</button>}
            <button className={styles.demoToggle} onClick={act(() => onToggleDemo(!demoMode))} disabled={busy}>
              {demoMode ? 'Turn demo off' : 'Turn demo on'}
            </button>
          </>
        ) : (
          <button className={styles.demoToggle} onClick={onLogin}>🔒 Operator login to run a drill</button>
        )}
      </div>
    </div>
  );
}
