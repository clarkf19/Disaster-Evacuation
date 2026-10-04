import { useState, useEffect, useRef, useCallback } from 'react';
import { reverseGeocode, searchPlaces } from '../services/routingApi';
import LiveAdvisoryCard from './LiveAdvisoryCard';
import styles from './RoutePlanner.module.css';

/** Straight-line distance in km (for sorting shelters by proximity). */
function distanceKm(a, b) {
  const R = 6371;
  const dLat = (b.lat - a.lat) * Math.PI / 180;
  const dLon = (b.lon - a.lon) * Math.PI / 180;
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(a.lat * Math.PI / 180) * Math.cos(b.lat * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export default function RoutePlanner({
  clickMode,
  setClickMode,
  source,
  dest,
  onClear,
  onCompute,
  loading,
  error,
  onSourceSet,
  onDestSet,
  routeResult,
  shelters = [],
}) {
  const [gpsLoading, setGpsLoading] = useState(false);
  const [gpsError, setGpsError] = useState('');
  const [shelterMode, setShelterMode] = useState(false);

  function handleGpsLocation() {
    if (!navigator.geolocation) {
      setGpsError('GPS not available in your browser.');
      return;
    }
    setGpsLoading(true);
    setGpsError('');
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude } = pos.coords;
        const name = await reverseGeocode(latitude, longitude);
        onSourceSet({ lat: latitude, lon: longitude, name });
        setGpsLoading(false);
      },
      () => {
        setGpsError('Could not access your location. Allow location access or set it manually.');
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

  const shownError = error || gpsError;

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h2>Evacuation Route Planner</h2>
        <p className={styles.sub}>Set locations by typing, using GPS, or clicking the map. Routes always avoid active hazard zones.</p>
      </div>

      <LocationSearch
        label="Start Location"
        icon="🔵"
        color="var(--google-blue)"
        value={source}
        onSelect={onSourceSet}
        clickModeKey="source"
        clickMode={clickMode}
        setClickMode={setClickMode}
        gpsSlot={
          <button
            className={styles.gpsBtn}
            onClick={handleGpsLocation}
            disabled={gpsLoading}
            title="Use my live GPS location"
            aria-label="Use my current location"
          >
            {gpsLoading ? <span className={styles.spinner} /> : '📡'}
          </button>
        }
      />

      <LocationSearch
        label="Destination"
        icon="🔴"
        color="var(--google-red)"
        value={dest}
        onSelect={onDestSet}
        clickModeKey="dest"
        clickMode={clickMode}
        setClickMode={setClickMode}
      />

      <button
        className={`${styles.shelterToggleBtn} ${shelterMode ? styles.shelterToggleActive : ''}`}
        onClick={() => setShelterMode(v => !v)}
      >
        <span>⛺</span>
        {shelterMode ? 'Hide Shelter List' : 'Evacuate → Choose a Shelter'}
        <span className={styles.shelterCount}>{shelters.filter(s => !s.unsafe).length}</span>
      </button>

      {shelterMode && (
        <ShelterPicker shelters={shelters} origin={source} onSelect={handleShelterPick} selectedDest={dest} />
      )}

      {(clickMode === 'source' || clickMode === 'dest') && (
        <div className={styles.tip}>
          <span>📍</span> Click anywhere on the map to pin your {clickMode === 'source' ? 'start' : 'destination'}
        </div>
      )}

      {shownError && <p className={styles.error} role="alert">{shownError}</p>}

      <div className={styles.buttons}>
        <button className={styles.btnPrimary} onClick={onCompute} disabled={loading || !source || !dest}>
          {loading ? <><span className={styles.spinner} /> Calculating route...</> : '⚡ Calculate Safe Route'}
        </button>

        {(source || dest || routeResult) && (
          <button className={styles.btnGhost} onClick={() => { onClear(); setShelterMode(false); }}>
            Clear Route
          </button>
        )}
      </div>

      {routeResult && !loading && <LiveAdvisoryCard result={routeResult} />}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────
   ShelterPicker — shelters sorted by distance from the start
   point (or by free space when no start is set). Unsafe
   shelters are shown but cannot be selected.
───────────────────────────────────────────────────────── */
function ShelterPicker({ shelters, origin, onSelect, selectedDest }) {
  const [query, setQuery] = useState('');

  const sorted = [...shelters]
    .map(s => ({
      ...s,
      pct: Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100)),
      km: origin ? distanceKm(origin, s) : null,
    }))
    .sort((a, b) => (a.unsafe - b.unsafe) || (origin ? a.km - b.km : a.pct - b.pct));

  const filtered = query.trim()
    ? sorted.filter(s => s.name.toLowerCase().includes(query.toLowerCase()))
    : sorted;

  return (
    <div className={styles.shelterPicker}>
      <div className={styles.shelterPickerHeader}>
        <span>⛺ {origin ? 'Nearest shelters first' : 'Least-full shelters first'}</span>
      </div>
      <input
        className={styles.shelterSearch}
        type="text"
        placeholder="Filter shelters..."
        value={query}
        onChange={e => setQuery(e.target.value)}
        aria-label="Filter shelters"
      />
      <div className={styles.shelterPickerList}>
        {filtered.map(s => {
          const barColor = s.unsafe ? '#94a3b8' : s.pct >= 90 ? '#ea4335' : s.pct >= 70 ? '#f97316' : '#34a853';
          const isSelected = selectedDest?.name?.startsWith(s.name);
          const disabled = s.unsafe || s.isFull;
          return (
            <button
              type="button"
              key={s.id}
              className={`${styles.shelterItem} ${isSelected ? styles.shelterItemSelected : ''}`}
              onClick={() => !disabled && onSelect(s)}
              disabled={disabled}
              style={disabled ? { opacity: 0.55, cursor: 'not-allowed' } : undefined}
            >
              <div className={styles.shelterItemTop}>
                <span className={styles.shelterItemName}>{s.name}</span>
                <span className={styles.shelterItemBadge} style={{ color: barColor, background: `${barColor}18` }}>
                  {s.unsafe ? '⚠️ Unsafe' : `${s.pct}%`}
                </span>
              </div>
              <div className={styles.shelterItemBar}>
                <div style={{ width: `${s.pct}%`, backgroundColor: barColor, height: '100%', borderRadius: 4 }} />
              </div>
              <div className={styles.shelterItemMeta}>
                <span>
                  {s.unsafe ? 'Inside an active hazard zone' : `${s.remainingCapacity?.toLocaleString()} spots available`}
                  {s.km != null && ` · ${s.km.toFixed(1)} km`}
                </span>
                {isSelected && <span className={styles.shelterItemCheck}>✓ Selected</span>}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────
   LocationSearch — type → autocomplete, pin button → map
   click, GPS button (source only)
───────────────────────────────────────────────────────── */
function LocationSearch({
  label, icon, color,
  value, onSelect,
  clickModeKey, clickMode, setClickMode,
  gpsSlot,
}) {
  const [query, setQuery]             = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen]               = useState(false);
  const [searching, setSearching]     = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const debounceRef = useRef(null);
  const latestQuery = useRef('');
  const wrapRef     = useRef(null);

  useEffect(() => {
    function handleClick(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  useEffect(() => () => clearTimeout(debounceRef.current), []);

  useEffect(() => {
    setQuery(value?.name || '');
  }, [value?.name]);

  const handleInputChange = useCallback((e) => {
    const val = e.target.value;
    setQuery(val);
    setOpen(true);
    setSelectedIndex(-1);
    clearTimeout(debounceRef.current);
    latestQuery.current = val;
    if (val.trim().length < 2) { setSuggestions([]); setSearching(false); return; }
    setSearching(true);
    debounceRef.current = setTimeout(async () => {
      const results = await searchPlaces(val);
      // Ignore responses for queries the user has already typed past.
      if (latestQuery.current !== val) return;
      setSuggestions(results);
      setSearching(false);
    }, 300);
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
  const inputId = `loc-${clickModeKey}`;

  return (
    <div className={styles.locationGroup} ref={wrapRef}>
      <label className={styles.locationLabel} htmlFor={inputId}>
        <span className={styles.locationIcon} aria-hidden="true">{icon}</span>
        {label}
      </label>
      <div className={styles.inputRow}>
        <div className={styles.inputWrap} style={isMapActive ? { outline: `2px solid ${color}` } : {}}>
          <input
            id={inputId}
            className={styles.locationInput}
            type="text"
            placeholder="Search a place in Greater Mumbai or Thane…"
            value={query}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => { if (suggestions.length > 0) setOpen(true); }}
            autoComplete="off"
            role="combobox"
            aria-expanded={open && suggestions.length > 0}
            aria-controls={`${inputId}-list`}
          />
          {searching && <span className={styles.spinnerInline} />}
        </div>
        <button
          className={`${styles.mapPinBtn} ${isMapActive ? styles.mapPinActive : ''}`}
          onClick={handleMapPin}
          title="Pick on map"
          aria-label={`Pick ${label.toLowerCase()} on the map`}
          style={isMapActive ? { background: color, color: '#fff' } : {}}
        >
          📍
        </button>
        {gpsSlot}
      </div>

      {open && query.trim().length >= 2 && !searching && suggestions.length === 0 && (
        <p className={styles.noResults}>No places found in the mapped area.</p>
      )}

      {open && suggestions.length > 0 && (
        <ul className={styles.dropdown} id={`${inputId}-list`} role="listbox">
          {suggestions.map((s, i) => (
            <li
              key={`${s.lat},${s.lon},${i}`}
              role="option"
              aria-selected={i === selectedIndex}
              className={`${styles.dropdownItem} ${i === selectedIndex ? styles.dropdownItemSelected : ''}`}
              onMouseDown={() => handleSuggestionClick(s)}
            >
              <div className={styles.suggIconWrap}>{s.icon || '📍'}</div>
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
