import { useState } from 'react';
import styles from './ShelterPanel.module.css';

const PAGE = 30;

function distanceKm(a, b) {
  const dLat = (b.lat - a.lat) * 111.32;
  const dLon = (b.lon - a.lon) * 111.32 * Math.cos(a.lat * Math.PI / 180);
  return Math.sqrt(dLat * dLat + dLon * dLon);
}

export default function ShelterPanel({ shelters, origin, dataVerified, demoMode, hazardsActive, onToggleDemo, onSelectShelter }) {
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(PAGE);
  const q = query.trim().toLowerCase();
  const sorted = shelters
    .filter(s => !q || s.name.toLowerCase().includes(q))
    .map(s => ({ ...s, km: origin ? distanceKm(origin, s) : null }))
    .sort((a, b) => (a.unsafe - b.unsafe)
      || (origin ? a.km - b.km : (b.currentOccupancy - a.currentOccupancy) || a.name.localeCompare(b.name)));
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
        <div className={`${styles.demoBanner} ${demoMode ? styles.demoOn : ''}`}>
          <div>
            <b>{demoMode ? '🎬 Demo mode: simulated arrivals' : 'Demo mode is off'}</b>
            <p>
              {!demoMode
                ? 'Occupancy changes only when operators update it.'
                : hazardsActive
                  ? `People from active hazard zones are heading to the nearest safe shelters${arrivingNow ? ` (+${arrivingNow.toLocaleString()} just arrived)` : ''}.`
                  : totalPeople > 0
                    ? 'No active hazards — shelters are emptying as people return home.'
                    : 'Place a hazard zone in the Hazards tab and watch shelters fill up.'}
            </p>
          </div>
          {onToggleDemo && (
            <button className={styles.demoToggle} onClick={() => onToggleDemo(!demoMode)}>
              {demoMode ? 'Turn off' : 'Turn on'}
            </button>
          )}
        </div>
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
                {onSelectShelter && (
                  <button
                    className={styles.navBtn}
                    onClick={() => onSelectShelter(s)}
                    disabled={s.unsafe || s.isFull}
                  >
                    {s.unsafe ? 'Unavailable — inside hazard zone' : s.isFull ? 'Full' : '📍 Set as Evacuation Destination'}
                  </button>
                )}
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
