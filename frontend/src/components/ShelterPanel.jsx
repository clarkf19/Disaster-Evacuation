import styles from './ShelterPanel.module.css';

export default function ShelterPanel({ shelters, dataVerified, demoMode, hazardsActive, onToggleDemo, onSelectShelter }) {
  const sorted = [...shelters].sort((a, b) => (a.unsafe - b.unsafe) || a.name.localeCompare(b.name));
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
            ⚠️ Shelter locations and capacities are placeholder data, not the official BMC list.
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

      <div className={styles.shelterList}>
        {shelters.length === 0 ? (
          <p className={styles.empty}>Loading shelters...</p>
        ) : (
          sorted.map((s) => {
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
                {s.floodProne && <p className={styles.note}>Low-lying site — not used during floods.</p>}
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
      </div>
    </div>
  );
}
