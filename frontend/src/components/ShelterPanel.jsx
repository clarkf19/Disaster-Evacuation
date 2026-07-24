import React from 'react';
import styles from './ShelterPanel.module.css';

export default function ShelterPanel({ shelters, onSelectShelter }) {
  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h3>Mumbai Evacuation Shelters</h3>
        <p className={styles.sub}>
          Real-time capacity and occupancy metrics across 22 designated emergency centers.
        </p>
      </div>

      <div className={styles.shelterList}>
        {shelters.length === 0 ? (
          <p className={styles.empty}>Loading shelters...</p>
        ) : (
          shelters.map((s) => {
            const pct = Math.min(
              100,
              Math.round((s.currentOccupancy / s.totalCapacity) * 100)
            );
            let barColor = '#34a853'; // Green
            if (pct >= 90) barColor = '#ea4335'; // Red
            else if (pct >= 70) barColor = '#f97316'; // Orange

            return (
              <div key={s.id || s.name} className={styles.card}>
                <div className={styles.cardHeader}>
                  <span className={styles.name}>{s.name}</span>
                  <span className={styles.badge} style={{ backgroundColor: `${barColor}15`, color: barColor }}>
                    {pct}% Full
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
                  <div
                    className={styles.progressFill}
                    style={{ width: `${pct}%`, backgroundColor: barColor }}
                  />
                </div>
                {onSelectShelter && (
                  <button
                    className={styles.navBtn}
                    onClick={() => onSelectShelter(s)}
                  >
                    📍 Set as Evacuation Destination
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
