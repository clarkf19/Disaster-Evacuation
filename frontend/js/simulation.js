// simulation.js — route computation, disaster injection, shelter updates

// ── Route ────────────────────────────────────────────────────────
async function computeRoute() {
    const src = parseInt(document.getElementById('source-input').value);
    const dst = parseInt(document.getElementById('dest-input').value);
    const algo = 'ASTAR'; // Defaults internally without technical term exposure

    if (!src || !dst) {
        alert('Please click the map to select both source and destination nodes.');
        return;
    }

    const btn = document.getElementById('compute-btn');
    btn.textContent = 'Computing...';
    btn.disabled = true;

    try {
        const res = await API.route(src, dst, algo);

        btn.textContent = 'Compute Route';
        btn.disabled = false;

        if (!res.pathFound) {
            alert('No path found between those nodes. The area may be fully blocked by disaster events.');
            return;
        }

        // Render polyline on map
        renderRoute(res.rawCoordinates);

        // Populate result card
        document.getElementById('res-dist').textContent  = res.totalDistanceKm.toFixed(2) + ' km';
        document.getElementById('res-time').textContent  = res.totalTravelTimeMinutes.toFixed(1) + ' min';
        document.getElementById('res-nodes').textContent = res.nodesExplored.toLocaleString();
        document.getElementById('res-exec').textContent  = res.executionTimeMs.toFixed(2) + ' ms';
        document.getElementById('route-result').classList.remove('hidden');
    } catch (e) {
        btn.textContent = 'Compute Route';
        btn.disabled = false;
        alert('Error computing route: ' + e.message);
    }
}

// ── Disasters ────────────────────────────────────────────────────
function startPlacingDisaster() {
    clickMode = 'disaster';
    document.getElementById('placing-indicator').classList.remove('hidden');
    document.getElementById('map-hint').innerHTML =
        'Click on map to place <span id="click-mode-label" style="color:#ef4444">disaster</span>';
}

async function placeDisasterAt(lat, lng) {
    const type     = document.getElementById('disaster-type').value;
    const radius   = parseFloat(document.getElementById('disaster-radius').value) || 800;
    const action   = document.getElementById('disaster-action').value;
    const blockRoads = action === 'block';
    const congestionMultiplier = blockRoads ? 1.0 : 3.0;
    const id = `${type.toLowerCase()}-${Date.now()}`;

    const req = {
        id, type,
        latitude: lat, longitude: lng,
        radiusMeters: radius,
        blockRoads, congestionMultiplier,
        description: `${type} at [${lat.toFixed(4)}, ${lng.toFixed(4)}]`
    };

    try {
        await API.addDisaster(req);
        addDisasterCircle({ ...req, lat, lon: lng });
        addDisasterToSidebar(req);

        // If a route is currently displayed, auto-recompute it
        const src = parseInt(document.getElementById('source-input').value);
        const dst = parseInt(document.getElementById('dest-input').value);
        if (src && dst) {
            setTimeout(computeRoute, 100);
        }
    } catch (e) {
        alert('Failed to add disaster: ' + e.message);
    }
}

function addDisasterToSidebar(d) {
    const list = document.getElementById('active-disasters-list');
    const div = document.createElement('div');
    div.className = 'disaster-tag';
    div.id = `dtag-${d.id}`;
    div.innerHTML = `
        <span><span class="dtype">${d.type}</span><br>
        <small style="color:var(--text-muted)">${d.radiusMeters}m · ${d.blockRoads ? 'Blocked' : d.congestionMultiplier + 'x congestion'}</small></span>
        <button title="Remove" onclick="removeDisaster('${d.id}')">✕</button>`;
    list.appendChild(div);
}

async function removeDisaster(id) {
    try {
        await API.removeDisaster(id);
        removeDisasterCircle(id);
        const tag = document.getElementById(`dtag-${id}`);
        if (tag) tag.remove();

        // Recompute route if active
        const src = parseInt(document.getElementById('source-input').value);
        const dst = parseInt(document.getElementById('dest-input').value);
        if (src && dst) setTimeout(computeRoute, 100);
    } catch (e) {
        alert('Failed to remove disaster: ' + e.message);
    }
}

async function clearAllDisasters() {
    try {
        await API.clearDisasters();
        clearAllDisasterCircles();
        document.getElementById('active-disasters-list').innerHTML = '';

        // Recompute route if active
        const src = parseInt(document.getElementById('source-input').value);
        const dst = parseInt(document.getElementById('dest-input').value);
        if (src && dst) setTimeout(computeRoute, 100);
    } catch (e) {
        alert('Failed to clear disasters: ' + e.message);
    }
}

// ── Shelter Refresh ──────────────────────────────────────────────
async function refreshShelters() {
    try {
        const shelters = await API.shelters();
        renderShelters(shelters);
    } catch (e) {
        console.warn('Shelter refresh failed:', e.message);
    }
}

// Auto-refresh shelters every 10 seconds
setInterval(refreshShelters, 10000);
