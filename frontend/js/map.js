/**
 * Leaflet.js Map Controller — Google Maps Inspired Light Theme with Live Segment Traffic.
 */

let map;
let nodesData = [];
let nodeLookup = new Map();
let sheltersData = [];

// Layer Groups
let nodeMarkersLayer;
let routePolylineLayer;
let disasterCirclesLayer;
let shelterMarkersLayer;
let evacueeMarkersLayer;
let evacueeRoutesLayer;

// Interaction State
let clickMode = 'source'; // 'source', 'dest', or 'disaster'
let sourceNodeId = null;
let destNodeId = null;
let sourceNodeCoords = null; // { lat, lon, name }
let destNodeCoords = null;   // { lat, lon, name }
let showShelters = true;

document.addEventListener('DOMContentLoaded', () => {
    initMap();
    loadGraphData();
});

function initMap() {
    // Center on Greater Mumbai
    map = L.map('map', {
        center: [19.0760, 72.8777],
        zoom: 11,
        zoomControl: true
    });

    // CartoDB Voyager Light tiles — Crisp, vibrant Google Maps style background
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 19
    }).addTo(map);

    // Initialize Layer Groups
    nodeMarkersLayer = L.layerGroup().addTo(map);
    routePolylineLayer = L.layerGroup().addTo(map);
    disasterCirclesLayer = L.layerGroup().addTo(map);
    shelterMarkersLayer = L.layerGroup().addTo(map);
    evacueeMarkersLayer = L.layerGroup().addTo(map);
    evacueeRoutesLayer = L.layerGroup().addTo(map);

    // Map Click Listener
    map.on('click', handleMapClick);
}

async function loadGraphData() {
    showLoading(true);
    try {
        const nodes = await API.getAllNodes();
        nodesData = nodes;
        nodeLookup.clear();
        nodes.forEach(n => nodeLookup.set(n.id, n));

        const shelters = await API.getAllShelters();
        sheltersData = shelters;
        
        // Update ONLY Shelters count on KPI bar
        const shelterStatEl = document.getElementById('stat-shelters');
        if (shelterStatEl) shelterStatEl.innerText = shelters.length;

        renderSheltersOnMap(shelters);
        renderShelterList(shelters);

        showLoading(false);
    } catch (e) {
        console.error("Failed to load graph data:", e);
        showLoading(false);
    }
}

function handleMapClick(e) {
    const lat = e.latlng.lat;
    const lon = e.latlng.lng;

    if (clickMode === 'disaster') {
        placeDisasterAtLocation(lat, lon);
    } else {
        findAndSelectNearestNode(lat, lon);
    }
}

async function findAndSelectNearestNode(lat, lon) {
    try {
        const nearest = await API.getNearestNode(lat, lon);
        if (!nearest || !nearest.id) return;

        const displayName = nearest.name || 'Selected Location';

        if (clickMode === 'source') {
            sourceNodeId = nearest.id;
            sourceNodeCoords = { lat: nearest.lat, lon: nearest.lon, name: displayName };
            document.getElementById('source-input').value = displayName;
            clickMode = 'dest';
            updateClickModeLabel('destination');
            highlightSelectedNode(nearest, '#1a73e8', displayName);
        } else if (clickMode === 'dest') {
            destNodeId = nearest.id;
            destNodeCoords = { lat: nearest.lat, lon: nearest.lon, name: displayName };
            document.getElementById('dest-input').value = displayName;
            clickMode = 'source';
            updateClickModeLabel('start');
            highlightSelectedNode(nearest, '#ea4335', displayName);
        }
    } catch (e) {
        console.error("Failed to find nearest location:", e);
    }
}

function highlightSelectedNode(node, color, label) {
    const marker = L.circleMarker([node.lat, node.lon], {
        radius: 9,
        fillColor: color,
        color: '#ffffff',
        weight: 2.5,
        fillOpacity: 0.95
    });
    marker.bindPopup(`<b>${label}</b>`).openPopup();
    nodeMarkersLayer.addLayer(marker);
}

function updateClickModeLabel(mode) {
    const label = document.getElementById('click-mode-label');
    if (label) label.innerText = mode;
}

// ---- Render Live Route Segments with Traffic Congestion Colors ----
function renderLiveRouteOnMap(res) {
    routePolylineLayer.clearLayers();
    if (!res || !res.pathFound) return;

    if (res.segmentDetails && res.segmentDetails.length > 0) {
        const bounds = [];
        res.segmentDetails.forEach(seg => {
            let color = '#1a73e8'; // Clear Google Blue
            if (seg.congestionFactor >= 2.0) color = '#ea4335'; // Heavy Red
            else if (seg.congestionFactor >= 1.3) color = '#fbbc04'; // Moderate Yellow

            const polyline = L.polyline([[seg.startLat, seg.startLon], [seg.endLat, seg.endLon]], {
                color: color,
                weight: 6,
                opacity: 0.9
            });
            routePolylineLayer.addLayer(polyline);
            bounds.push([seg.startLat, seg.startLon]);
            bounds.push([seg.endLat, seg.endLon]);
        });
        if (bounds.length > 0) {
            map.fitBounds(L.latLngBounds(bounds), { padding: [40, 40] });
        }
    } else if (res.rawCoordinates) {
        const latlngs = res.rawCoordinates.map(c => [c[0], c[1]]);
        const polyline = L.polyline(latlngs, { color: '#1a73e8', weight: 6, opacity: 0.9 }).addTo(routePolylineLayer);
        map.fitBounds(polyline.getBounds(), { padding: [40, 40] });
    }
}

// ---- Render Shelters ----
function renderSheltersOnMap(shelters) {
    shelterMarkersLayer.clearLayers();
    if (!showShelters) return;

    shelters.forEach(s => {
        const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
        let color = '#34a853'; // Google Green <70%
        if (pct >= 100) color = '#ea4335'; // Google Red full
        else if (pct >= 70) color = '#f97316'; // Orange heavy

        const iconHtml = `
            <div class="shelter-pin" style="border-color: ${color};">
                <span class="shelter-icon">⛺</span>
                <div class="shelter-badge">${pct}%</div>
            </div>
        `;

        const customIcon = L.divIcon({
            html: iconHtml,
            className: 'custom-shelter-icon',
            iconSize: [36, 36],
            iconAnchor: [18, 18]
        });

        const marker = L.marker([s.lat, s.lon], { icon: customIcon });
        marker.bindPopup(`
            <div class="popup-title"><b>${s.name}</b></div>
            <div class="popup-sub">Capacity: <b>${s.currentOccupancy.toLocaleString()} / ${s.totalCapacity.toLocaleString()}</b></div>
            <div class="popup-sub">Remaining: <b>${s.remainingCapacity.toLocaleString()}</b></div>
        `);

        shelterMarkersLayer.addLayer(marker);
    });
}

function renderShelterList(shelters) {
    const listEl = document.getElementById('shelter-list');
    if (!listEl) return;

    if (!shelters || shelters.length === 0) {
        listEl.innerHTML = '<p class="empty-msg">No shelters available.</p>';
        return;
    }

    listEl.innerHTML = shelters.map(s => {
        const pct = Math.min(100, Math.round((s.currentOccupancy / s.totalCapacity) * 100));
        let barClass = 'green';
        if (pct >= 100) barClass = 'red';
        else if (pct >= 70) barClass = 'orange';

        return `
            <div class="shelter-card">
                <div class="shelter-card-header">
                    <span>${s.name}</span>
                    <small>${s.currentOccupancy.toLocaleString()} / ${s.totalCapacity.toLocaleString()}</small>
                </div>
                <div class="progress-bar-bg">
                    <div class="progress-bar-fill ${barClass}" style="width: ${pct}%;"></div>
                </div>
            </div>
        `;
    }).join('');
}

function toggleShelters() {
    showShelters = !showShelters;
    const btn = document.getElementById('toggle-shelters-btn');
    if (btn) btn.innerText = showShelters ? 'Hide Map Markers' : 'Show Map Markers';
    renderSheltersOnMap(sheltersData);
}

// ---- Render Evacuee Groups & Routes ----
function renderEvacueeGroupsOnMap(groups) {
    evacueeMarkersLayer.clearLayers();
    evacueeRoutesLayer.clearLayers();

    if (!groups) return;

    groups.forEach((g, idx) => {
        const srcNode = nodeLookup.get(g.sourceNodeId);
        if (srcNode) {
            const iconHtml = `
                <div class="evac-pin">
                    <span class="evac-icon">🚨</span>
                    <div class="evac-badge">${(g.count / 1000).toFixed(1)}k</div>
                </div>
            `;

            const icon = L.divIcon({
                html: iconHtml,
                className: 'custom-evac-icon',
                iconSize: [34, 34],
                iconAnchor: [17, 17]
            });

            const marker = L.marker([srcNode.lat, srcNode.lon], { icon: icon });
            marker.bindPopup(`
                <b>${g.name}</b><br>
                Area: ${g.wardName}<br>
                Evacuees: <b>${g.count.toLocaleString()}</b><br>
                Status: <span class="status-badge ${g.status.toLowerCase()}">${g.status}</span><br>
                Assigned Shelter: <b>${g.assignedShelterName || 'Unassigned'}</b>
            `);
            evacueeMarkersLayer.addLayer(marker);
        }

        if (g.routeCoordinates && g.routeCoordinates.length > 0) {
            const latlngs = g.routeCoordinates.map(c => [c[0], c[1]]);
            const polyline = L.polyline(latlngs, {
                color: getRouteColor(idx),
                weight: 5,
                opacity: 0.85
            });
            polyline.bindPopup(`Evacuation Route for <b>${g.name}</b> (${Math.round(g.travelTimeMinutes)} mins, ${g.travelDistanceKm.toFixed(1)} km)`);
            evacueeRoutesLayer.addLayer(polyline);
        }
    });
}

function getRouteColor(index) {
    const palette = ['#1a73e8', '#34a853', '#ea4335', '#fbbc04', '#8e24aa', '#00acc1'];
    return palette[index % palette.length];
}

function showLoading(show) {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = show ? 'flex' : 'none';
    }
}
