import { useState, useEffect, useCallback, useRef } from 'react';
import MapView from './components/MapView';
import RoutePlanner from './components/RoutePlanner';
import DisasterPanel from './components/DisasterPanel';
import ShelterPanel from './components/ShelterPanel';
import CommandCentre from './components/CommandCentre';
import EmergencyChatbot from './components/EmergencyChatbot';
import ErrorBoundary from './components/ErrorBoundary';
import OperatorLogin from './components/OperatorLogin';
import { calcLiveRoute, reverseGeocode } from './services/routingApi';
import * as API from './services/backendApi';
import styles from './App.module.css';

const POLL_MS = 20_000;
const FAST_POLL_MS = 4_000;
/** While the backend is unreachable (e.g. a free-tier server waking up), retry quickly. */
const WAKE_POLL_MS = 3_000;
/** How long to call it "waking up" before calling it "offline". */
const WAKE_GRACE_MS = 120_000;

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
  const [demo, setDemo] = useState(null);           // demo-mode / drill status from the server

  // Operator mode: unlocked once the server accepts the operator token
  const [operatorOk, setOperatorOk] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);

  // Map layers
  const [hotspots, setHotspots] = useState([]);
  const [stations, setStations] = useState([]);
  const [layers, setLayers] = useState({ hotspots: true, stations: false });

  // Route state
  const [travelMode, setTravelMode] = useState('DRIVE'); // DRIVE | WALK | TRANSIT
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

  // When the backend first became unreachable (null while it is reachable).
  const [failingSince, setFailingSince] = useState(null);

  const refreshLiveData = useCallback(async () => {
    try {
      // One round trip for everything the UI polls.
      const live = await API.getLiveData();
      setConfig(live.config);
      setShelters(live.shelters || []);
      setDisasters(live.disasters || []);
      setStations(live.stations || []);
      setDemo(live.demo || null);
      setOnline(true);
      setFailingSince(null);
    } catch {
      setOnline(false);
      setFailingSince(since => since ?? Date.now());
    }
  }, []);

  // A token kept from earlier in this tab is re-checked once the server answers.
  const operatorChecked = useRef(false);
  useEffect(() => {
    if (!online || operatorChecked.current) return;
    operatorChecked.current = true;
    if (API.getOperatorToken()) {
      API.verifyOperatorToken(API.getOperatorToken()).then(setOperatorOk).catch(() => {});
    }
  }, [online]);

  const operatorRequired = config?.operatorTokenRequired !== false;
  const operatorMode = !operatorRequired || operatorOk;

  /** Runs an operator action; a rejected token locks operator mode again and asks to log in. */
  const runOperatorAction = useCallback(async (action, failureText) => {
    try {
      await action();
      return true;
    } catch (e) {
      if (e.status === 401) {
        setOperatorOk(false);
        setLoginOpen(true);
        showNotice('Operator login needed for this action.');
      } else {
        showNotice(`${failureText}: ${e.message}`);
      }
      return false;
    } finally {
      refreshLiveData();
    }
  }, [refreshLiveData, showNotice]);

  function logoutOperator() {
    API.setOperatorToken('');
    setOperatorOk(false);
    showNotice('Logged out of operator mode.', 'info');
  }

  // Hotspots never change at runtime: load once the backend is reachable.
  useEffect(() => {
    if (online && hotspots.length === 0) {
      API.getFloodHotspots().then(d => setHotspots(d.hotspots || [])).catch(() => {});
    }
  }, [online, hotspots.length]);

  // Poll fast while demo mode is moving people (hazards active, or shelters still emptying),
  // and very fast while the server is waking up so data appears as soon as it is ready.
  const sheltersChanging = config?.demoMode
    && (disasters.length > 0 || demo?.drill?.active || shelters.some(s => s.currentOccupancy > 0));
  const pollMs = online === false ? WAKE_POLL_MS : sheltersChanging ? FAST_POLL_MS : POLL_MS;
  const wakingUp = online === false && failingSince != null && Date.now() - failingSince < WAKE_GRACE_MS;

  useEffect(() => {
    refreshLiveData();
  }, [refreshLiveData]);

  useEffect(() => {
    const id = setInterval(refreshLiveData, pollMs);
    return () => clearInterval(id);
  }, [refreshLiveData, pollMs]);

  const computeRoute = useCallback(async (from, to, mode) => {
    if (!from || !to) return;
    setRouteError('');
    setRouteLoading(true);
    try {
      setRouteResult(await calcLiveRoute(from.lat, from.lon, to.lat, to.lon, mode));
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
      computeRoute(source, dest, travelMode);
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
      const config = pendingDisasterConfig;
      setClickMode(null);
      setPendingDisasterConfig(null);
      const placed = await runOperatorAction(() => API.addDisaster({
        type: config.type,
        latitude: lat,
        longitude: lon,
        radiusMeters: config.radius,
        blockRoads: block,
        congestionMultiplier: block ? 1.0 : 3.5,
        description: `Reported ${config.type.replace('_', ' ').toLowerCase()} hazard`,
      }), 'Could not place the hazard');
      if (placed) showNotice('Hazard zone placed — routes now avoid it.', 'info');
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
    setDest({ lat: shelter.lat, lon: shelter.lon, name: `${shelter.name} (${pct}% full)`, shelterId: shelter.id });
    showNotice(source
      ? `✓ ${shelter.name} set as your destination — press Calculate Safe Route.`
      : `✓ ${shelter.name} set as your destination — now choose your start point.`, 'info');
  }

  const statusLabel = wakingUp ? 'Waking up'
    : online === false ? 'Offline'
    : online ? (config?.liveTrafficEnabled ? 'Live traffic' : 'Online') : 'Connecting';

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
            className={`${styles.liveBadge} ${wakingUp ? styles.wakingBadge : online === false ? styles.offlineBadge : ''}`}
            title={online === false ? 'Cannot reach the backend server yet' : 'Backend reachable'}
          >
            <span className={styles.liveDot} />
            {statusLabel}
          </div>
        </div>

        {wakingUp && (
          <div className={styles.wakingBanner} role="status">
            <span className={styles.wakingSpinner} aria-hidden="true" />
            Waking up the server — this can take up to a minute after a quiet period. The page will fill in automatically.
          </div>
        )}
        {online === false && !wakingUp && (
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
          <div className={styles.kpiCard}>
            <span className={styles.kpiValue}>
              {shelters.reduce((sum, s) => sum + (s.currentOccupancy || 0), 0).toLocaleString()}
            </span>
            <span className={styles.kpiLabel}>People sheltered</span>
          </div>
        </div>

        {operatorRequired && config && (
          <div className={`${styles.operatorBar} ${operatorMode ? styles.operatorOn : ''}`}>
            {operatorMode ? (
              <>
                <span>🔓 <b>Operator mode</b> — you can change live hazards and run drills</span>
                <button className={styles.operatorBtn} onClick={logoutOperator}>Log out</button>
              </>
            ) : (
              <>
                <span>👁️ Viewer mode — live hazards and drills are for operators</span>
                <button className={styles.operatorBtn} onClick={() => setLoginOpen(true)}>🔒 Operator login</button>
              </>
            )}
          </div>
        )}

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
          <ErrorBoundary>
            {activeTab === 'route' && (
              <RoutePlanner
                clickMode={clickMode}
                setClickMode={setClickMode}
                source={source}
                dest={dest}
                onClear={handleClearRoute}
                onCompute={() => computeRoute(source, dest, travelMode)}
                travelMode={travelMode}
                onTravelModeChange={(m) => {
                  setTravelMode(m);
                  if (routeResult && source && dest) computeRoute(source, dest, m);
                }}
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
                operatorMode={operatorMode}
                onLogin={() => setLoginOpen(true)}
                runOperatorAction={runOperatorAction}
                onPlaceMode={handleStartDisasterPlace}
              />
            )}
            {activeTab === 'shelters' && (
              <ShelterPanel
                shelters={shelters}
                origin={source}
                dataVerified={config?.shelterDataVerified}
                demoMode={config?.demoMode}
                demo={demo}
                hazardsActive={disasters.length > 0}
                operatorMode={operatorMode}
                onLogin={() => setLoginOpen(true)}
                onToggleDemo={enabled => runOperatorAction(() => API.setDemoMode(enabled), 'Could not change demo mode')}
                onStartDrill={async () => {
                  if (await runOperatorAction(API.startDrill, 'Could not start the drill')) {
                    showNotice('Evacuation drill started — watch the shelters fill up on the map.', 'info');
                  }
                }}
                onStopDrill={() => runOperatorAction(API.stopDrill, 'Could not stop the drill')}
                destinationShelterId={dest?.shelterId}
                onPlanRoute={() => setActiveTab('route')}
                onSelectShelter={handleSelectShelter}
              />
            )}
            {activeTab === 'command' && (
              <CommandCentre simulation={simulation} onSimulationChange={setSimulation} />
            )}
          </ErrorBoundary>
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
          hotspots={hotspots}
          stations={stations}
          layers={layers}
          onToggleLayer={(key) => setLayers(l => ({ ...l, [key]: !l[key] }))}
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
        {loginOpen && (
          <OperatorLogin
            onClose={() => setLoginOpen(false)}
            onUnlocked={() => {
              setOperatorOk(true);
              setLoginOpen(false);
              showNotice('🔓 Operator mode unlocked.', 'info');
            }}
          />
        )}
      </main>
    </div>
  );
}
