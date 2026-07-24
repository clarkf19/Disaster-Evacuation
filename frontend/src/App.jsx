import React, { useState, useEffect } from 'react';
import MapView from './components/MapView';
import RoutePlanner from './components/RoutePlanner';
import DisasterPanel from './components/DisasterPanel';
import ShelterPanel from './components/ShelterPanel';
import { reverseGeocode } from './services/tomtomApi';
import * as API from './services/backendApi';
import styles from './App.module.css';

export default function App() {
  const [activeTab, setActiveTab] = useState('route');

  // Map interaction state
  const [clickMode, setClickMode] = useState(null); // 'source' | 'dest' | 'disaster' | null
  const [source, setSource] = useState(null);       // { lat, lon, name }
  const [dest, setDest] = useState(null);           // { lat, lon, name }
  const [pendingDisasterConfig, setPendingDisasterConfig] = useState(null);

  // Data state
  const [shelters, setShelters] = useState([]);
  const [disasters, setDisasters] = useState([]);
  const [routeResult, setRouteResult] = useState(null);

  useEffect(() => {
    fetchShelters();
    fetchDisasters();
  }, []);

  async function fetchShelters() {
    try {
      const data = await API.getAllShelters();
      setShelters(data || []);
    } catch (e) {
      console.error('Failed to load shelters:', e);
    }
  }

  async function fetchDisasters() {
    try {
      const data = await API.listDisasters();
      setDisasters(data || []);
    } catch (e) {
      console.error('Failed to load disasters:', e);
    }
  }

  // Handle map clicks — reverse geocoding is done via backend proxy (no key in frontend)
  async function handleMapClick(lat, lon) {
    if (!clickMode) return;

    if (clickMode === 'source') {
      const name = await reverseGeocode(lat, lon);
      setSource({ lat, lon, name });
      setClickMode('dest');
    } else if (clickMode === 'dest') {
      const name = await reverseGeocode(lat, lon);
      setDest({ lat, lon, name });
      setClickMode(null);
    } else if (clickMode === 'disaster' && pendingDisasterConfig) {
      const disasterReq = {
        id: `disaster-${Date.now()}`,
        type: pendingDisasterConfig.type,
        latitude: lat,
        longitude: lon,
        radiusMeters: pendingDisasterConfig.radius,
        blockRoads: pendingDisasterConfig.action === 'block',
        congestionMultiplier: pendingDisasterConfig.action === 'block' ? 1.0 : 3.5,
        description: `Reported ${pendingDisasterConfig.type} hazard`,
      };
      try {
        await API.addDisaster(disasterReq);
        await fetchDisasters();
      } catch (e) {
        console.error('Failed to place disaster:', e);
      } finally {
        setClickMode(null);
        setPendingDisasterConfig(null);
      }
    }
  }

  function handleStartDisasterPlace(config) {
    setPendingDisasterConfig(config);
    setClickMode('disaster');
  }

  function handleClearRoute() {
    setSource(null);
    setDest(null);
    setRouteResult(null);
    setClickMode(null);
  }

  // Called by RoutePlanner when GPS or text search sets a point directly
  function handleSourceSet(point) {
    setSource(point);
    setClickMode(null);
  }

  function handleDestSet(point) {
    setDest(point);
    setClickMode(null);
  }

  // Called when user selects a shelter from ShelterPanel or MapView popup
  function handleSelectShelter(shelter) {
    const pct = Math.min(100, Math.round((shelter.currentOccupancy / shelter.totalCapacity) * 100));
    setDest({
      lat: shelter.lat,
      lon: shelter.lon,
      name: `${shelter.name} (${pct}% full)`,
    });
    setActiveTab('route');
  }

  return (
    <div className={styles.appContainer}>
      {/* ======== SIDEBAR ======== */}
      <aside className={styles.sidebar}>

        {/* Header — no API key button */}
        <div className={styles.sidebarHeader}>
          <div className={styles.logoRow}>
            <div className={styles.logoIcon}>🚨</div>
            <div>
              <h1>Mumbai Evac</h1>
              <p className={styles.subtitle}>Live Traffic & Evacuation System</p>
            </div>
          </div>
          <div className={styles.liveBadge}>
            <span className={styles.liveDot} />
            Live
          </div>
        </div>

        {/* KPI Bar */}
        <div className={styles.statusBar}>
          <div className={styles.kpiCard}>
            <span className={styles.kpiValue}>{shelters.length}</span>
            <span className={styles.kpiLabel}>Evac Shelters</span>
          </div>
          <div className={styles.kpiCard}>
            <span className={`${styles.kpiValue} ${styles.accentGreen}`}>
              {disasters.length > 0 ? `${disasters.length} Active` : 'All Clear'}
            </span>
            <span className={styles.kpiLabel}>Disaster Events</span>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className={styles.tabNav}>
          <button
            className={`${styles.tabBtn} ${activeTab === 'route' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('route')}
          >
            ⚡ Live Route
          </button>
          <button
            className={`${styles.tabBtn} ${activeTab === 'disasters' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('disasters')}
          >
            ⚠️ Disasters {disasters.length > 0 && `(${disasters.length})`}
          </button>
          <button
            className={`${styles.tabBtn} ${activeTab === 'shelters' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('shelters')}
          >
            ⛺ Shelters
          </button>
        </div>

        {/* Tab Contents */}
        <div className={styles.tabContent}>
          {activeTab === 'route' && (
            <RoutePlanner
              clickMode={clickMode}
              setClickMode={setClickMode}
              source={source}
              dest={dest}
              onClear={handleClearRoute}
              onRouteResult={setRouteResult}
              onSourceSet={handleSourceSet}
              onDestSet={handleDestSet}
              hasDisaster={disasters.length > 0}
              routeResult={routeResult}
              shelters={shelters}
            />
          )}
          {activeTab === 'disasters' && (
            <DisasterPanel
              disasters={disasters}
              onDisastersChange={fetchDisasters}
              onPlaceMode={handleStartDisasterPlace}
            />
          )}
          {activeTab === 'shelters' && (
            <ShelterPanel shelters={shelters} onSelectShelter={handleSelectShelter} />
          )}
        </div>
      </aside>

      {/* ======== MAP ======== */}
      <main className={styles.mapContainer}>
        <MapView
          clickMode={clickMode}
          onMapClick={handleMapClick}
          source={source}
          dest={dest}
          routeResult={routeResult}
          shelters={shelters}
          disasters={disasters}
          onSelectShelter={handleSelectShelter}
        />
        {clickMode && clickMode !== 'disaster' && (
          <div className={styles.mapClickHint}>
            📍 Click map to set <b>{clickMode === 'source' ? 'START' : 'DESTINATION'}</b> point
            <button className={styles.cancelClickBtn} onClick={() => setClickMode(null)}>
              Cancel
            </button>
          </div>
        )}
        {clickMode === 'disaster' && (
          <div className={styles.mapClickHint} style={{ borderColor: '#ea4335' }}>
            ⚠️ Click map to place <b>DISASTER EPICENTER</b>
            <button className={styles.cancelClickBtn} onClick={() => setClickMode(null)}>
              Cancel
            </button>
          </div>
        )}
      </main>
    </div>
  );
}
