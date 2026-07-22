// map.js — Visually Stunning Esri Dark Canvas Map, Markers, and Route Rendering

let map;
let routeOuterPolyline = null;
let routeInnerPolyline = null;
let sourceMarker = null;
let destMarker = null;
let shelterMarkers = [];
let disasterCircles = {};
let sheltersVisible = true;

const MUMBAI_CENTER = [19.076, 72.8777];
const MUMBAI_ZOOM = 12;

// Click mode: 'source' | 'dest' | 'disaster'
let clickMode = 'source';

function initMap() {
    map = L.map('map', {
        center: MUMBAI_CENTER,
        zoom: MUMBAI_ZOOM,
        zoomControl: false
    });

    // 1. Esri World Dark Gray Base Map (ultra-smooth dark slate canvas)
    L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Tiles &copy; Esri &mdash; Esri, DeLorme, NAVTEQ',
        maxZoom: 16,
        subdomains: []
    }).addTo(map);

    // 2. Esri World Dark Gray Reference Layer (crisp, elegant silver-white road & area labels)
    L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}', {
        attribution: '',
        maxZoom: 16,
        pane: 'shadowPane' // renders below markers but above base map
    }).addTo(map);

    L.control.zoom({ position: 'topright' }).addTo(map);

    // Map click listener
    map.on('click', onMapClick);

    loadInitialData();
}

async function loadInitialData() {
    try {
        const stats = await API.graphStats();
        document.getElementById('stat-nodes').textContent = stats.nodeCount.toLocaleString();
        document.getElementById('stat-edges').textContent = stats.edgeCount.toLocaleString();

        const shelters = await API.shelters();
        document.getElementById('stat-shelters').textContent = shelters.length;
        renderShelters(shelters);

        document.getElementById('loading-overlay').classList.add('hidden');
    } catch (e) {
        console.warn('Backend connection:', e.message);
        document.getElementById('loading-overlay').innerHTML =
            '<p style="color:#f97316;font-weight:600;">Backend not reachable.<br><small>Start the Spring Boot server on :8080</small></p>';
    }
}

// ── Visually Stunning Dual-Layer Glowing Route Polyline ─────────────
function renderRoute(coords) {
    if (routeOuterPolyline) map.removeLayer(routeOuterPolyline);
    if (routeInnerPolyline) map.removeLayer(routeInnerPolyline);

    if (!coords || coords.length < 2) return;

    const latlngs = coords.map(c => [c[0], c[1]]);

    // Outer Neon Glow Layer
    routeOuterPolyline = L.polyline(latlngs, {
        color: '#f97316',
        weight: 12,
        opacity: 0.35,
        lineCap: 'round',
        lineJoin: 'round'
    }).addTo(map);

    // Inner Sharp Vibrant Line
    routeInnerPolyline = L.polyline(latlngs, {
        color: '#ff8c00',
        weight: 4.5,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round'
    }).addTo(map);

    map.fitBounds(routeInnerPolyline.getBounds(), { padding: [60, 60] });
}

function clearRoute() {
    if (routeOuterPolyline) { map.removeLayer(routeOuterPolyline); routeOuterPolyline = null; }
    if (routeInnerPolyline) { map.removeLayer(routeInnerPolyline); routeInnerPolyline = null; }
    if (sourceMarker)       { map.removeLayer(sourceMarker); sourceMarker = null; }
    if (destMarker)         { map.removeLayer(destMarker); destMarker = null; }

    document.getElementById('source-input').value = '';
    document.getElementById('dest-input').value = '';
    document.getElementById('route-result').classList.add('hidden');
}

// ── Shelters with Elegant SVG Markers ─────────────────────────────
function renderShelters(shelters) {
    shelterMarkers.forEach(m => map.removeLayer(m));
    shelterMarkers = [];

    const listEl = document.getElementById('shelter-list');
    listEl.innerHTML = '';

    shelters.forEach(s => {
        const pct = (s.currentOccupancy / s.totalCapacity) * 100;
        const cls = pct > 80 ? 'full' : pct > 50 ? 'warn' : '';

        // Glowing Emerald Marker
        const icon = L.divIcon({
            className: '',
            html: `<div style="
                width:26px;height:26px;
                background:linear-gradient(135deg, #10b981 0%, #059669 100%);
                border:2px solid rgba(255,255,255,0.7);
                border-radius:50%;
                display:flex;align-items:center;justify-content:center;
                box-shadow:0 0 14px rgba(16, 185, 129, 0.6);
            "><svg width="13" height="13" viewBox="0 0 24 24" fill="white"><path d="M12 3L2 12h3v8h14v-8h3L12 3z"/></svg></div>`,
            iconSize: [26, 26], iconAnchor: [13, 13]
        });

        const marker = L.marker([s.lat, s.lon], { icon })
            .bindPopup(`
                <div style="font-weight:700;font-size:0.85rem;margin-bottom:4px;color:#10b981;">${s.name}</div>
                <div style="font-size:0.75rem;color:#94a3b8;margin-bottom:2px;">Capacity: <strong style="color:#f8fafc;">${s.currentOccupancy.toLocaleString()} / ${s.totalCapacity.toLocaleString()}</strong></div>
                <div style="font-size:0.75rem;color:#94a3b8;">Status: ${s.isFull ? '<strong style="color:#ef4444;">FULL</strong>' : '<strong style="color:#10b981;">Available</strong>'}</div>
            `)
            .addTo(map);

        shelterMarkers.push(marker);

        const div = document.createElement('div');
        div.className = 'shelter-item';
        div.innerHTML = `
            <div class="s-name">${s.name}</div>
            <div style="color:var(--text-secondary);font-size:0.69rem">${s.currentOccupancy.toLocaleString()} / ${s.totalCapacity.toLocaleString()} occupied</div>
            <div class="cap-bar"><div class="cap-fill ${cls}" style="width:${Math.min(100,pct).toFixed(1)}%"></div></div>`;
        listEl.appendChild(div);
    });
}

function toggleShelters() {
    sheltersVisible = !sheltersVisible;
    shelterMarkers.forEach(m => sheltersVisible ? m.addTo(map) : map.removeLayer(m));
    document.getElementById('toggle-shelters-btn').textContent =
        sheltersVisible ? 'Hide Shelters' : 'Show Shelters';
}

// ── Disaster Zones ────────────────────────────────────────────────
function addDisasterCircle(disaster) {
    const colors = {
        FLOOD: '#3b82f6',
        FIRE: '#ef4444',
        BRIDGE_COLLAPSE: '#f59e0b',
        CHEMICAL_LEAK: '#8b5cf6'
    };
    const color = colors[disaster.type] || '#f97316';

    const circle = L.circle([disaster.lat, disaster.lon], {
        radius: disaster.radiusMeters,
        color,
        fillColor: color,
        fillOpacity: 0.18,
        weight: 2,
        dashArray: '8 6'
    }).bindPopup(`
        <div style="font-weight:700;font-size:0.85rem;color:${color};">${disaster.type} Zone</div>
        <div style="font-size:0.75rem;margin-top:4px;">Radius: ${disaster.radiusMeters}m</div>
        <div style="font-size:0.75rem;color:#f8fafc;margin-top:2px;">Impact: ${disaster.blockRoads ? 'Roads Blocked' : `${disaster.congestionMultiplier}x Congestion`}</div>
    `).addTo(map);

    const pulse = L.circleMarker([disaster.lat, disaster.lon], {
        radius: 8, color: color, fillColor: color, fillOpacity: 0.95, weight: 2
    }).addTo(map);

    disasterCircles[disaster.id] = { circle, pulse };
}

function removeDisasterCircle(id) {
    if (disasterCircles[id]) {
        map.removeLayer(disasterCircles[id].circle);
        map.removeLayer(disasterCircles[id].pulse);
        delete disasterCircles[id];
    }
}

function clearAllDisasterCircles() {
    Object.keys(disasterCircles).forEach(removeDisasterCircle);
}

// ── Interactive Map Click Handler ─────────────────────────────────
async function onMapClick(e) {
    const { lat, lng } = e.latlng;

    if (clickMode === 'disaster') {
        await placeDisasterAt(lat, lng);
        clickMode = 'source';
        document.getElementById('placing-indicator').classList.add('hidden');
        document.getElementById('map-hint').innerHTML = 'Click map to set <span id="click-mode-label">source</span> node';
        return;
    }

    try {
        const node = await API.nearest(lat, lng);
        if (clickMode === 'source') {
            document.getElementById('source-input').value = node.id;
            if (sourceMarker) map.removeLayer(sourceMarker);

            sourceMarker = L.circleMarker([node.lat, node.lon], {
                radius: 11,
                color: '#ffffff',
                fillColor: '#f97316',
                fillOpacity: 1,
                weight: 3,
                className: 'marker-pulse-orange'
            }).bindPopup(`<strong>Source Node</strong><br>ID: ${node.id}`).addTo(map);

            clickMode = 'dest';
            document.getElementById('click-mode-label').textContent = 'destination';
        } else {
            document.getElementById('dest-input').value = node.id;
            if (destMarker) map.removeLayer(destMarker);

            destMarker = L.circleMarker([node.lat, node.lon], {
                radius: 11,
                color: '#ffffff',
                fillColor: '#3b82f6',
                fillOpacity: 1,
                weight: 3,
                className: 'marker-pulse-blue'
            }).bindPopup(`<strong>Destination Node</strong><br>ID: ${node.id}`).addTo(map);

            clickMode = 'source';
            document.getElementById('click-mode-label').textContent = 'source';
        }
    } catch (err) {
        console.warn('Could not resolve nearest node:', err);
    }
}

document.addEventListener('DOMContentLoaded', initMap);
