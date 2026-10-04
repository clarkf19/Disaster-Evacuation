import { useState, useEffect, useCallback, useRef } from 'react';
import MapView from './components/MapView';
import RoutePlanner from './components/RoutePlanner';
import DisasterPanel from './components/DisasterPanel';
import ShelterPanel from './components/ShelterPanel';
import CommandCentre from './components/CommandCentre';
import EmergencyChatbot from './components/EmergencyChatbot';
import { calcLiveRoute, reverseGeocode } from './services/routingApi';
import * as API from './services/backendApi';
import styles from './App.module.css';

const POLL_MS = 20_000;

export default function App() {
  const [activeTab, setActiveTab] = useState('route');

  // Map interaction state
  const [clickMode, setClickMode] = useState(null); // 'source' | 'dest' | 'disaster' | null
  const [source, setSource] = useState(null);       // { lat, lon, name }
  const [dest, setDest] = useState(null);           // { lat, lon, name }
  const [pendingDisasterConfig, setPendingDisasterConfig] = useState(null);

  // Live data
  const [config, setConfig] = useState(null);
  const [online, setOnline] = useState(null);       // null = checking, true/false after first poll
  const [shelters, setShelters] = useState([]);
  const [disasters, setDisasters] = useState([]);

  // Route state
  const [routeResult, setRouteResult] = useState(null);
  const [routeLoading, setRouteLoading] = useState(false);
  const [routeError, setRouteError] = useState('');

  // Command Centre simulation shown on the map: { comparison, strategy }
  const [simulation, setSimulation] = useState(null);

  // Transient message shown over the map
  const [notice, setNotice] = useState(null);
  const noticeTimer = useRef(null);
  const showNotice = useCallback((text, kind = 'error') => {
    setNotice({ text, kind });
    clearTimeout(noticeTimer.current);
    noticeTimer.current = setTimeout(() => setNotice(null), 7000);
  }, []);

  const refreshLiveData = useCallback(async () => {
    try {
      const [cfg, shelterData, disasterData] = await Promise.all([
        API.getConfig(), API.getAllShelters(), API.listDisasters(),
      ]);
      setConfig(cfg);
      setShelters(shelterData || []);
      setDisasters(disasterData || []);
      setOnline(true);
    } catch {
      setOnline(false);
    }
  }, []);

  useEffect(() => {
    refreshLiveData();
    const id = setInterval(refreshLiveData, POLL_MS);
    return () => clearInterval(id);
  }, [refreshLiveData]);

  const computeRoute = useCallback(async (from, to) => {
    if (!from || !to) return;
    setRouteError('');
    setRouteLoading(true);
    try {
      setRouteResult(await calcLiveRoute(from.lat, from.lon, to.lat, to.lon));
    } catch (e) {
      setRouteError(e.message);
      setRouteResult(null);
    } finally {
      setRouteLoading(false);
    }
  }, []);

  // When the set of active hazards changes, re-plan the route currently on screen.
  const disasterSignature = disasters.map(d => d.id).sort().join('|');
  const lastSignature = useRef(disasterSignature);
  useEffect(() => {
    if (lastSignature.current === disasterSignature) return;
    lastSignature.current = disasterSignature;
    if (routeResult && source && dest) {
      showNotice('Active hazards changed — route recalculated.', 'info');
      computeRoute(source, dest);
    }
    // routeResult intentionally omitted: only hazard changes should trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disasterSignature]);

  async function handleMapClick(lat, lon) {
    if (!clickMode) return;

    if (clickMode === 'source') {
      setClickMode('dest');
      setSource({ lat, lon, name: 'Locating…' });
      setSource({ lat, lon, name: await reverseGeocode(lat, lon) });
    } else if (clickMode === 'dest') {
      setClickMode(null);
      setDest({ lat, lon, name: 'Locating…' });
      setDest({ lat, lon, name: await reverseGeocode(lat, lon) });
    } else if (clickMode === 'disaster' && pendingDisasterConfig) {
      const block = pendingDisasterConfig.action === 'block';
      try {
        await API.addDisaster({
          type: pendingDisasterConfig.type,
          latitude: lat,
          longitude: lon,
          radiusMeters: pendingDisasterConfig.radius,
          blockRoads: block,
          congestionMultiplier: block ? 1.0 : 3.5,
          description: `Reported ${pendingDisasterConfig.type.replace('_', ' ').toLowerCase()} hazard`,
        });
        await refreshLiveData();
      } catch (e) {
        showNotice(e.status === 401
          ? 'Operator token required to place live disasters — enter it in the Disasters tab.'
          : `Could not place disaster: ${e.message}`);
      } finally {
        setClickMode(null);
        setPendingDisasterConfig(null);
      }
    }
  }

  function handleStartDisasterPlace(placement) {
    setPendingDisasterConfig(placement);
    setClickMode('disaster');
  }

  function handleClearRoute() {
    setSource(null);
    setDest(null);
    setRouteResult(null);
    setRouteError('');
    setClickMode(null);
  }

  function handleSourceSet(point) {
    setSource(point);
    setClickMode(null);
  }

  function handleDestSet(point) {
    setDest(point);
    setClickMode(null);
  }

  // Called when the user picks a shelter from the Shelters tab or a map popup
  function handleSelectShelter(shelter) {
    if (shelter.unsafe) {
      showNotice(`${shelter.name} is currently unsafe (inside a hazard zone). Pick another shelter.`);
      return;
    }
    const pct = Math.min(100, Math.round((shelter.currentOccupancy / shelter.totalCapacity) * 100));
    setDest({ lat: shelter.lat, lon: shelter.lon, name: `${shelter.name} (${pct}% full)` });
    setActiveTab('route');
  }

  const statusLabel = online === false ? 'Offline' : online ? (config?.liveTrafficEnabled ? 'Live traffic' : 'Online') : 'Connecting';

  return (
    <div className={styles.appContainer}>
      {/* ======== SIDEBAR ======== */}
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <div className={styles.logoRow}>
            <div className={styles.logoIcon} aria-hidden="true">🚨</div>
            <div>
              <h1>Mumbai Evac</h1>
              <p className={styles.subtitle}>Hazard-aware evacuation planning</p>
            </div>
          </div>
          <div
            className={`${styles.liveBadge} ${online === false ? styles.offlineBadge : ''}`}
            title={online === false ? 'Cannot reach the backend server' : 'Backend reachable'}
          >
            <span className={styles.liveDot} />
            {statusLabel}
          </div>
        </div>

        {online === false && (
          <div className={styles.offlineBanner} role="alert">
            Server unreachable — showing last known data. In a life-threatening emergency call <b>112</b> or BMC <b>1916</b>.
          </div>
        )}

        {/* KPI Bar */}
        <div className={styles.statusBar}>
          <div className={styles.kpiCard}>
            <span className={styles.kpiValue}>
              {shelters.filter(s => !s.unsafe && !s.isFull).length}/{shelters.length}
            </span>
            <span className={styles.kpiLabel}>Shelters open</span>
          </div>
          <div className={styles.kpiCard}>
            <span className={`${styles.kpiValue} ${disasters.length ? styles.accentRed : styles.accentGreen}`}>
              {disasters.length > 0 ? `${disasters.length} Active` : 'All Clear'}
            </span>
            <span className={styles.kpiLabel}>Hazard zones</span>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className={styles.tabNav} aria-label="Sections">
          {[
            ['route', '⚡ Route'],
            ['disasters', `⚠️ Hazards${disasters.length ? ` (${disasters.length})` : ''}`],
            ['shelters', '⛺ Shelters'],
            ['command', '📊 Command'],
          ].map(([key, label]) => (
            <button
              key={key}
              className={`${styles.tabBtn} ${activeTab === key ? styles.activeTab : ''}`}
              onClick={() => setActiveTab(key)}
              aria-current={activeTab === key ? 'page' : undefined}
            >
              {label}
            </button>
          ))}
        </nav>

        <div className={styles.tabContent}>
          {activeTab === 'route' && (
            <RoutePlanner
              clickMode={clickMode}
              setClickMode={setClickMode}
              source={source}
              dest={dest}
              onClear={handleClearRoute}
              onCompute={() => computeRoute(source, dest)}
              loading={routeLoading}
              error={routeError}
              onSourceSet={handleSourceSet}
              onDestSet={handleDestSet}
              routeResult={routeResult}
              shelters={shelters}
            />
          )}
          {activeTab === 'disasters' && (
            <DisasterPanel
              disasters={disasters}
              operatorTokenRequired={config?.operatorTokenRequired}
              onDisastersChange={refreshLiveData}
              onPlaceMode={handleStartDisasterPlace}
              onError={showNotice}
            />
          )}
          {activeTab === 'shelters' && (
            <ShelterPanel
              shelters={shelters}
              dataVerified={config?.shelterDataVerified}
              onSelectShelter={handleSelectShelter}
            />
          )}
          {activeTab === 'command' && (
            <CommandCentre simulation={simulation} onSimulationChange={setSimulation} />
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
          simulation={activeTab === 'command' ? simulation : null}
          coverageBounds={config?.coverageBounds}
          onSelectShelter={handleSelectShelter}
        />
        {clickMode && clickMode !== 'disaster' && (
          <div className={styles.mapClickHint}>
            📍 Click map to set <b>{clickMode === 'source' ? 'START' : 'DESTINATION'}</b> point
            <button className={styles.cancelClickBtn} onClick={() => setClickMode(null)}>Cancel</button>
          </div>
        )}
        {clickMode === 'disaster' && (
          <div className={`${styles.mapClickHint} ${styles.mapClickHintDanger}`}>
            ⚠️ Click map to place <b>HAZARD CENTRE</b>
            <button className={styles.cancelClickBtn} onClick={() => setClickMode(null)}>Cancel</button>
          </div>
        )}
        {notice && (
          <div className={`${styles.notice} ${notice.kind === 'info' ? styles.noticeInfo : ''}`} role="status">
            {notice.text}
            <button className={styles.cancelClickBtn} onClick={() => setNotice(null)} aria-label="Dismiss">✕</button>
          </div>
        )}

        <EmergencyChatbot userLocation={source} />
      </main>
    </div>
  );
}
