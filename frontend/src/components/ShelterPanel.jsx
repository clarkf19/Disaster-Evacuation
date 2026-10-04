import styles from './ShelterPanel.module.css';

export default function ShelterPanel({ shelters, dataVerified, onSelectShelter }) {
  const sorted = [...shelters].sort((a, b) => (a.unsafe - b.unsafe) || a.name.localeCompare(b.name));

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
                  <span className={styles.name}>{s.name}</span>
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
