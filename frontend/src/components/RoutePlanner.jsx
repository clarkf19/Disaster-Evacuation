import { useState, useEffect, useRef, useCallback } from 'react';
import { calcLiveRoute, reverseGeocode, searchPlaces } from '../services/tomtomApi';
import LiveAdvisoryCard from './LiveAdvisoryCard';
import styles from './RoutePlanner.module.css';

const MUMBAI_CENTER = { lat: 19.076, lon: 72.8777 };

export default function RoutePlanner({
  clickMode,
  setClickMode,
  source,
  dest,
  onClear,
  onRouteResult,
  onSourceSet,
  onDestSet,
  hasDisaster,
  routeResult,
  shelters = [],
}) {
  const [loading, setLoading]       = useState(false);
  const [error, setError]           = useState('');
  const [gpsLoading, setGpsLoading] = useState(false);
  const [shelterMode, setShelterMode] = useState(false); // show shelter picker for destination

  async function handleCompute() {
    if (!source || !dest) {
      setError('Please set both a start and destination location.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const result = await calcLiveRoute(source.lat, source.lon, dest.lat, dest.lon);
      onRouteResult(result);
    } catch (e) {
      setError(e.message || 'Failed to calculate route. Ensure the backend server is running.');
      onRouteResult(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleGpsLocation() {
    if (!navigator.geolocation) {
      setError('GPS not available in your browser.');
      return;
    }
    setGpsLoading(true);
    setError('');
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude } = pos.coords;
        const name = await reverseGeocode(latitude, longitude);
        onSourceSet({ lat: latitude, lon: longitude, name });
        setGpsLoading(false);
      },
      () => {
        setError('Could not access your location. Please allow location access or set it manually.');
        setGpsLoading(false);
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  }

  function handleShelterPick(s) {
    const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
    onDestSet({ lat: s.lat, lon: s.lon, name: `${s.name} (${pct}% full)` });
    setShelterMode(false);
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h2>Live Route Planner</h2>
        <p className={styles.sub}>Set locations by typing, using GPS, or clicking the map.</p>
      </div>

      {/* ── Source ── */}
      <LocationSearch
        label="Start Location"
        icon="🔵"
        color="var(--google-blue)"
        value={source}
        onSelect={onSourceSet}
        clickModeKey="source"
        clickMode={clickMode}
        setClickMode={setClickMode}
        biasLat={source?.lat || MUMBAI_CENTER.lat}
        biasLon={source?.lon || MUMBAI_CENTER.lon}
        gpsSlot={
          <button
            className={styles.gpsBtn}
            onClick={handleGpsLocation}
            disabled={gpsLoading}
            title="Use my live GPS location"
          >
            {gpsLoading ? <span className={styles.spinner} /> : '📡'}
          </button>
        }
      />

      {/* ── Destination ── */}
      <LocationSearch
        label="Destination"
        icon="🔴"
        color="var(--google-red)"
        value={dest}
        onSelect={onDestSet}
        clickModeKey="dest"
        clickMode={clickMode}
        setClickMode={setClickMode}
        biasLat={source?.lat || MUMBAI_CENTER.lat}
        biasLon={source?.lon || MUMBAI_CENTER.lon}
      />

      {/* ── Evacuate to Shelter button ── */}
      <button
        className={`${styles.shelterToggleBtn} ${shelterMode ? styles.shelterToggleActive : ''}`}
        onClick={() => setShelterMode(v => !v)}
      >
        <span>⛺</span>
        {shelterMode ? 'Hide Shelter List' : 'Evacuate → Choose Nearest Shelter'}
        <span className={styles.shelterCount}>{shelters.length}</span>
      </button>

      {/* ── Shelter picker list ── */}
      {shelterMode && (
        <ShelterPicker shelters={shelters} onSelect={handleShelterPick} selectedDest={dest} />
      )}

      {/* Map-click tip */}
      {(clickMode === 'source' || clickMode === 'dest') && (
        <div className={styles.tip}>
          <span>📍</span> Click anywhere on the map to pin your {clickMode === 'source' ? 'start' : 'destination'}
        </div>
      )}

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.buttons}>
        <button
          className={styles.btnPrimary}
          onClick={handleCompute}
          disabled={loading || !source || !dest}
        >
          {loading ? (
            <><span className={styles.spinner} /> Calculating Live Route...</>
          ) : (
            '⚡ Calculate Live Route'
          )}
        </button>

        {(source || dest || routeResult) && (
          <button className={styles.btnGhost} onClick={() => { onClear(); setShelterMode(false); }}>
            Clear Route
          </button>
        )}
      </div>

      {routeResult && !loading && (
        <LiveAdvisoryCard result={routeResult} hasDisaster={hasDisaster} />
      )}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────
   ShelterPicker — sorted list of shelters the user can
   select to auto-fill as destination
───────────────────────────────────────────────────────── */
function ShelterPicker({ shelters, onSelect, selectedDest }) {
  const [query, setQuery] = useState('');

  const sorted = [...shelters]
    .map(s => ({
      ...s,
      pct: Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100)),
    }))
    .sort((a, b) => a.pct - b.pct); // least full first

  const filtered = query.trim()
    ? sorted.filter(s => s.name.toLowerCase().includes(query.toLowerCase()))
    : sorted;

  return (
    <div className={styles.shelterPicker}>
      <div className={styles.shelterPickerHeader}>
        <span>⛺ Select an evacuation shelter as destination</span>
      </div>
      <input
        className={styles.shelterSearch}
        type="text"
        placeholder="Filter shelters..."
        value={query}
        onChange={e => setQuery(e.target.value)}
      />
      <div className={styles.shelterPickerList}>
        {filtered.map(s => {
          const barColor = s.pct >= 90 ? '#ea4335' : s.pct >= 70 ? '#f97316' : '#34a853';
          const isSelected = selectedDest?.name?.startsWith(s.name);
          return (
            <div
              key={s.id || s.name}
              className={`${styles.shelterItem} ${isSelected ? styles.shelterItemSelected : ''}`}
              onClick={() => onSelect(s)}
            >
              <div className={styles.shelterItemTop}>
                <span className={styles.shelterItemName}>{s.name}</span>
                <span className={styles.shelterItemBadge} style={{ color: barColor, background: `${barColor}18` }}>
                  {s.pct}%
                </span>
              </div>
              <div className={styles.shelterItemBar}>
                <div style={{ width: `${s.pct}%`, backgroundColor: barColor, height: '100%', borderRadius: 4 }} />
              </div>
              <div className={styles.shelterItemMeta}>
                <span>{s.remainingCapacity?.toLocaleString()} spots available</span>
                {isSelected && <span className={styles.shelterItemCheck}>✓ Selected</span>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────
   LocationSearch — smart input: type → autocomplete,
   pin button → map click, GPS button (source only)
───────────────────────────────────────────────────────── */
function LocationSearch({
  label, icon, color,
  value, onSelect,
  clickModeKey, clickMode, setClickMode,
  biasLat, biasLon,
  gpsSlot,
}) {
  const [query, setQuery]             = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen]               = useState(false);
  const [searching, setSearching]     = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const debounceRef = useRef(null);
  const wrapRef     = useRef(null);

  useEffect(() => {
    function handleClick(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  useEffect(() => {
    if (value?.name) setQuery(value.name);
  }, [value?.name]);

  const handleInputChange = useCallback((e) => {
    const val = e.target.value;
    setQuery(val);
    setOpen(true);
    setSelectedIndex(-1);
    clearTimeout(debounceRef.current);
    if (val.trim().length < 2) { setSuggestions([]); return; }
    setSearching(true);
    debounceRef.current = setTimeout(async () => {
      const results = await searchPlaces(val);
      setSuggestions(results);
      setSearching(false);
    }, 280);
  }, []);


  function handleSuggestionClick(s) {
    onSelect({ lat: s.lat, lon: s.lon, name: s.name });
    setQuery(s.name);
    setSuggestions([]);
    setOpen(false);
  }

  function handleKeyDown(e) {
    if (!open || suggestions.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => (prev < suggestions.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => (prev > 0 ? prev - 1 : suggestions.length - 1));
    } else if (e.key === 'Enter' && selectedIndex >= 0 && selectedIndex < suggestions.length) {
      e.preventDefault();
      handleSuggestionClick(suggestions[selectedIndex]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  function handleMapPin() {
    setClickMode(clickMode === clickModeKey ? null : clickModeKey);
    setOpen(false);
  }

  const isMapActive = clickMode === clickModeKey;

  return (
    <div className={styles.locationGroup} ref={wrapRef}>
      <label className={styles.locationLabel}>
        <span className={styles.locationIcon}>{icon}</span>
        {label}
      </label>
      <div className={styles.inputRow}>
        <div className={styles.inputWrap} style={isMapActive ? { outline: `2px solid ${color}` } : {}}>
          <input
            className={styles.locationInput}
            type="text"
            placeholder="Type any location in Mumbai or click map..."
            value={query}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => { if (suggestions.length > 0) setOpen(true); }}
            autoComplete="off"
          />
          {searching && <span className={styles.spinnerInline} />}
        </div>
        <button
          className={`${styles.mapPinBtn} ${isMapActive ? styles.mapPinActive : ''}`}
          onClick={handleMapPin}
          title="Click to pin on map"
          style={isMapActive ? { background: color, color: '#fff' } : {}}
        >
          📍
        </button>
        {gpsSlot}
      </div>

      {open && suggestions.length > 0 && (
        <ul className={styles.dropdown}>
          {suggestions.map((s, i) => (
            <li
              key={i}
              className={`${styles.dropdownItem} ${i === selectedIndex ? styles.dropdownItemSelected : ''}`}
              onMouseDown={() => handleSuggestionClick(s)}
            >
              <div className={styles.suggIconWrap}>
                {s.icon || (s.type === 'POI' ? '🏢' : '📍')}
              </div>
              <div className={styles.suggTextWrap}>
                <span className={styles.suggName}>{s.name}</span>
                {s.subText && <span className={styles.suggSubText}>{s.subText}</span>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
