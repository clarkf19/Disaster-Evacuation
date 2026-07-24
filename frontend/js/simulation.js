/**
 * Simulation Controller & UI Interaction Layer — Real-Time Live Route Conditions.
 *
 * Design Decision: When the user has an active route computed, any disaster
 * injection automatically re-runs the route computation so the advisory card
 * and map segment colors immediately reflect the changed live conditions.
 * This creates the "real-time" feel without WebSockets — polling is deliberately
 * avoided; instead we piggyback on user-initiated actions (disaster add/remove).
 */

let activeDisasters = [];
let lastRouteResult = null;   // Cache last route for live re-render on condition change

document.addEventListener('DOMContentLoaded', () => {
    loadScenarios();
    refreshActiveDisastersList();
    // Hide delay row initially until a route is computed
    const delayRow = document.getElementById('delay-row');
    if (delayRow) delayRow.style.display = 'none';
});

// ---- Tab Navigation ----
function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    const activeContent = document.getElementById(tabId);
    if (activeContent) activeContent.classList.add('active');

    const activeBtn = Array.from(document.querySelectorAll('.tab-btn'))
        .find(btn => btn.getAttribute('onclick').includes(tabId));
    if (activeBtn) activeBtn.classList.add('active');
}

// ---- Preset Emergency Scenarios ----
async function loadScenarios() {
    try {
        const scenarios = await API.getPresetScenarios();
        const select = document.getElementById('scenario-select');
        if (!select) return;

        select.innerHTML = '<option value="">-- Choose Emergency Scenario --</option>' + 
            scenarios.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    } catch (e) {
        console.error("Failed to fetch scenarios:", e);
    }
}

async function onScenarioSelectChanged() {
    const select = document.getElementById('scenario-select');
    const descBox = document.getElementById('scenario-desc');
    if (!select || !descBox) return;

    const id = select.value;
    if (!id) {
        descBox.innerText = "Select an emergency scenario to model evacuee clusters and safety routes.";
        return;
    }

    const scenarios = await API.getPresetScenarios();
    const scenario = scenarios.find(s => s.id === id);
    if (scenario) {
        descBox.innerHTML = `<strong>${scenario.name}</strong>: ${scenario.description}`;
    }
}

async function loadAndRunSelectedScenario() {
    const select = document.getElementById('scenario-select');
    const id = select ? select.value : null;

    if (!id) {
        alert("Please select an emergency scenario first.");
        return;
    }

    showLoading(true);
    try {
        await API.loadPresetScenario(id);
        await API.runEvacuationSimulation('CAPACITY_AWARE');
        const updatedGroups = await API.getActiveEvacueeGroups();
        const updatedShelters = await API.getAllShelters();
        const disasters = await API.listDisasters();

        sheltersData = updatedShelters;
        renderSheltersOnMap(updatedShelters);
        renderShelterList(updatedShelters);
        renderEvacueeGroupsOnMap(updatedGroups);
        renderEvacueeGroupsList(updatedGroups);
        renderDisasterCirclesOnMap(disasters);
        refreshActiveDisastersList();

        // If a route is already plotted, refresh it against new live conditions
        if (lastRouteResult && sourceNodeId && destNodeId) {
            await recomputeAndRefreshRoute();
        }

        showLoading(false);
    } catch (e) {
        console.error("Failed to run scenario:", e);
        showLoading(false);
        alert("Error executing scenario simulation.");
    }
}

function renderEvacueeGroupsList(groups) {
    const container = document.getElementById('evacuee-groups-list');
    if (!container) return;

    if (!groups || groups.length === 0) {
        container.innerHTML = '<p class="empty-msg">No active evacuee groups loaded.</p>';
        return;
    }

    container.innerHTML = groups.map(g => {
        const roundedMins = Math.round(g.travelTimeMinutes);
        return `
            <div class="evacuee-card">
                <div class="evacuee-header">
                    <strong>${g.name}</strong>
                    <span class="status-badge ${g.status.toLowerCase()}">${g.status}</span>
                </div>
                <div class="evacuee-details">
                    <span>Evacuees: <b>${g.count.toLocaleString()}</b> (${g.wardName})</span><br>
                    <span>Assigned Shelter: <b>${g.assignedShelterName || 'None'}</b></span><br>
                    <span>Travel Time: <b>${roundedMins > 0 ? roundedMins + ' mins' : '—'}</b> (${g.travelDistanceKm > 0 ? g.travelDistanceKm.toFixed(1) + ' km' : '—'})</span>
                </div>
            </div>
        `;
    }).join('');
}

// ---- Strategy Benchmark Comparison ----
async function runBenchmarkComparison() {
    const select = document.getElementById('scenario-select');
    const scenarioName = select && select.options[select.selectedIndex] ? 
        select.options[select.selectedIndex].text : 'Active Evacuation Scenario';

    showLoading(true);
    try {
        const bench = await API.compareEvacuationStrategies(scenarioName);
        renderStrategyComparison(bench);
        switchTab('benchmark-tab');
        showLoading(false);
    } catch (e) {
        console.error("Strategy benchmark comparison failed:", e);
        showLoading(false);
        alert("Failed to run strategy comparison.");
    }
}

function renderStrategyComparison(bench) {
    const output = document.getElementById('strategy-comparison-output');
    if (!output || !bench) return;

    const naive = bench.naiveStrategyMetrics;
    const capacityAware = bench.capacityAwareStrategyMetrics;

    output.innerHTML = `
        <div class="comparison-card">
            <h3 class="comparison-title">${bench.scenarioName}</h3>
            
            <div class="metrics-grid">
                <div class="metric-column naive">
                    <h4>Baseline Strategy: Naive (Capacity-Blind)</h4>
                    <div class="metric-item"><span>Housed Evacuees:</span> <strong>${naive.evacueesSuccessfullyHoused.toLocaleString()} / ${naive.totalEvacuees.toLocaleString()}</strong></div>
                    <div class="metric-item overflow"><span>Overflow Evacuees:</span> <strong>${naive.overflowEvacuees.toLocaleString()}</strong></div>
                    <div class="metric-item"><span>Avg Travel Time:</span> <strong>${Math.round(naive.avgEvacuationTimeMinutes)} mins</strong></div>
                    <div class="metric-item"><span>Max Evacuation Time:</span> <strong>${Math.round(naive.maxEvacuationTimeMinutes)} mins</strong></div>
                    <div class="metric-item"><span>Avg Travel Distance:</span> <strong>${naive.avgTravelDistanceKm.toFixed(1)} km</strong></div>
                    <div class="metric-item"><span>Shelter Utilization:</span> <strong>${Math.round(naive.shelterUtilizationPercent)}%</strong></div>
                </div>

                <div class="metric-column aware">
                    <h4>Capacity-Aware Engine (Optimized)</h4>
                    <div class="metric-item"><span>Housed Evacuees:</span> <strong>${capacityAware.evacueesSuccessfullyHoused.toLocaleString()} / ${capacityAware.totalEvacuees.toLocaleString()}</strong></div>
                    <div class="metric-item overflow"><span>Overflow Evacuees:</span> <strong>${capacityAware.overflowEvacuees.toLocaleString()}</strong></div>
                    <div class="metric-item"><span>Avg Travel Time:</span> <strong>${Math.round(capacityAware.avgEvacuationTimeMinutes)} mins</strong></div>
                    <div class="metric-item"><span>Max Evacuation Time:</span> <strong>${Math.round(capacityAware.maxEvacuationTimeMinutes)} mins</strong></div>
                    <div class="metric-item"><span>Avg Travel Distance:</span> <strong>${capacityAware.avgTravelDistanceKm.toFixed(1)} km</strong></div>
                    <div class="metric-item"><span>Shelter Utilization:</span> <strong>${Math.round(capacityAware.shelterUtilizationPercent)}%</strong></div>
                </div>
            </div>

            <div class="benchmark-summary-box">
                <strong>Safety Analysis:</strong> The Capacity-Aware Engine prevents severe shelter overcrowding by distributing evacuee clusters to nearby shelters with available capacity and dynamically rerouting traffic around congested corridors.
            </div>
        </div>
    `;
}

// ---- Point-to-Point Route Calculation ----
async function computeRoute() {
    if (!sourceNodeId || !destNodeId) {
        alert("Please select both Start and Destination points by clicking on the map.");
        return;
    }

    showLoading(true);
    try {
        let res;
        if (sourceNodeCoords && destNodeCoords) {
            try {
                res = await API.computeTomTomRoute(sourceNodeCoords.lat, sourceNodeCoords.lon, destNodeCoords.lat, destNodeCoords.lon);
            } catch (tomtomErr) {
                console.warn("TomTom API call failed, falling back to backend graph calculation:", tomtomErr);
                res = await API.computeRoute(sourceNodeId, destNodeId, 'ASTAR');
            }
        } else {
            res = await API.computeRoute(sourceNodeId, destNodeId, 'ASTAR');
        }

        lastRouteResult = res;
        renderComputedRouteResult(res);
        showLoading(false);
    } catch (e) {
        console.error("Route calculation error:", e);
        showLoading(false);
    }
}

/**
 * Silent background re-computation — called after disaster events change
 * live conditions while a route is already displayed.
 */
async function recomputeAndRefreshRoute() {
    if (!sourceNodeId || !destNodeId) return;
    try {
        let res;
        if (sourceNodeCoords && destNodeCoords) {
            try {
                res = await API.computeTomTomRoute(sourceNodeCoords.lat, sourceNodeCoords.lon, destNodeCoords.lat, destNodeCoords.lon);
            } catch (tomtomErr) {
                res = await API.computeRoute(sourceNodeId, destNodeId, 'ASTAR');
            }
        } else {
            res = await API.computeRoute(sourceNodeId, destNodeId, 'ASTAR');
        }
        lastRouteResult = res;
        renderComputedRouteResult(res);
    } catch (e) {
        console.error("Background route refresh error:", e);
    }
}

/**
 * Renders the full live advisory card and route segments on map.
 *
 * Status mapping (from backend LiveRouteStatus):
 *  CLEAR            → green dot + green badge
 *  MODERATE_TRAFFIC → amber dot + amber badge + card warning shade
 *  HEAVY_CONGESTION → red dot  + red badge   + card danger shade
 *  DISASTER_BYPASS  → blue dot + blue badge  + card info shade
 *  UNPASSABLE       → red dot  + red badge   + card danger shade
 */
function renderComputedRouteResult(res) {
    const card = document.getElementById('route-result');
    if (!card) return;

    if (!res || !res.pathFound) {
        card.classList.remove('hidden');
        updateLiveAdvisory('UNPASSABLE', 'No safe route found. All corridors are blocked by active disasters or hazards.', 0);
        return;
    }

    card.classList.remove('hidden');

    // Distance & travel time
    document.getElementById('res-dist').innerText = `${res.totalDistanceKm.toFixed(1)} km`;
    document.getElementById('res-time').innerText = `${Math.round(res.totalTravelTimeMinutes)} mins`;

    // Live advisory card
    const delayMins = res.congestionDelayMinutes || 0;
    updateLiveAdvisory(
        res.liveRouteStatus || 'CLEAR',
        res.liveAdvisoryMessage || 'Live route clear.',
        delayMins
    );

    // Draw color-coded segment polylines on map
    renderLiveRouteOnMap(res);
}

/**
 * Updates the live advisory card UI with the correct color theme, badge, and delay row.
 */
function updateLiveAdvisory(status, message, delayMins) {
    const advisoryCard = document.getElementById('route-advisory-card');
    const statusBadge  = document.getElementById('res-live-status');
    const advisoryMsg  = document.getElementById('res-live-advisory');
    const delayRow     = document.getElementById('delay-row');
    const delayEl      = document.getElementById('res-delay');
    const liveDot      = document.querySelector('.live-dot');

    if (!advisoryCard || !statusBadge || !advisoryMsg) return;

    // Reset classes
    advisoryCard.className = 'live-advisory-card';
    statusBadge.className  = 'live-badge';
    if (liveDot) liveDot.style.backgroundColor = '';

    switch (status) {
        case 'CLEAR':
            statusBadge.classList.add('clear');
            statusBadge.innerText = '✅ Live Route Clear';
            if (liveDot) liveDot.style.backgroundColor = '#34a853';
            break;
        case 'MODERATE_TRAFFIC':
            advisoryCard.classList.add('warning');
            statusBadge.classList.add('moderate');
            statusBadge.innerText = '🟡 Moderate Traffic';
            if (liveDot) liveDot.style.backgroundColor = '#fbbc04';
            break;
        case 'HEAVY_CONGESTION':
            advisoryCard.classList.add('danger');
            statusBadge.classList.add('heavy');
            statusBadge.innerText = '🔴 Heavy Traffic Delay';
            if (liveDot) liveDot.style.backgroundColor = '#ea4335';
            break;
        case 'DISASTER_BYPASS':
            advisoryCard.classList.add('bypass');
            statusBadge.classList.add('bypass');
            statusBadge.innerText = '🔵 Live Disaster Bypass';
            if (liveDot) liveDot.style.backgroundColor = '#1a73e8';
            break;
        case 'UNPASSABLE':
            advisoryCard.classList.add('danger');
            statusBadge.classList.add('heavy');
            statusBadge.innerText = '🚫 Route Blocked';
            if (liveDot) liveDot.style.backgroundColor = '#ea4335';
            break;
        default:
            statusBadge.classList.add('clear');
            statusBadge.innerText = 'Live Route Clear';
    }

    advisoryMsg.innerText = message;

    // Traffic delay row — only show if there is a meaningful delay
    if (delayRow) {
        const roundedDelay = Math.round(delayMins);
        if (roundedDelay > 0) {
            delayRow.style.display = 'flex';
            if (delayEl) delayEl.innerText = `+${roundedDelay} min${roundedDelay !== 1 ? 's' : ''}`;
        } else {
            delayRow.style.display = 'none';
        }
    }
}

function clearRoute() {
    routePolylineLayer.clearLayers();
    nodeMarkersLayer.clearLayers();
    document.getElementById('source-input').value = '';
    document.getElementById('dest-input').value = '';
    document.getElementById('route-result').classList.add('hidden');
    const delayRow = document.getElementById('delay-row');
    if (delayRow) delayRow.style.display = 'none';
    sourceNodeId = null;
    destNodeId = null;
    lastRouteResult = null;
}

// ---- Disaster Injection ----
function startPlacingDisaster() {
    clickMode = 'disaster';
    const indicator = document.getElementById('placing-indicator');
    if (indicator) indicator.classList.remove('hidden');
}

async function placeDisasterAtLocation(lat, lon) {
    const type     = document.getElementById('disaster-type').value;
    const radius   = parseInt(document.getElementById('disaster-radius').value);
    const action   = document.getElementById('disaster-action').value;
    const isBlock  = action === 'block';

    const disasterReq = {
        id: `disaster-${Date.now()}`,
        type: type,
        latitude: lat,
        longitude: lon,
        radiusMeters: radius,
        blockRoads: isBlock,
        congestionMultiplier: isBlock ? 1.0 : 3.5,
        description: `Reported ${type} event`
    };

    showLoading(true);
    try {
        await API.addDisaster(disasterReq);
        clickMode = 'source';
        updateClickModeLabel('start');

        const indicator = document.getElementById('placing-indicator');
        if (indicator) indicator.classList.add('hidden');

        // Re-run active scenario simulation to trigger dynamic rerouting
        const select = document.getElementById('scenario-select');
        if (select && select.value) {
            await API.runEvacuationSimulation('CAPACITY_AWARE');
            const updatedGroups = await API.getActiveEvacueeGroups();
            renderEvacueeGroupsOnMap(updatedGroups);
            renderEvacueeGroupsList(updatedGroups);
        }

        const disasters = await API.listDisasters();
        renderDisasterCirclesOnMap(disasters);
        refreshActiveDisastersList();

        // KEY: If user has an active route, silently recompute it with new live conditions
        // This is how we deliver "real-time" route updates on disaster injection.
        if (lastRouteResult && sourceNodeId && destNodeId) {
            await recomputeAndRefreshRoute();
        }

        showLoading(false);
    } catch (e) {
        console.error("Disaster placement error:", e);
        showLoading(false);
    }
}

function renderDisasterCirclesOnMap(disasters) {
    disasterCirclesLayer.clearLayers();
    if (!disasters) return;

    disasters.forEach(d => {
        const circle = L.circle([d.lat, d.lon], {
            radius: d.radiusMeters,
            color: d.blockRoads ? '#ea4335' : '#fbbc04',
            fillColor: d.blockRoads ? '#ea4335' : '#fbbc04',
            fillOpacity: 0.30,
            weight: 2.5
        });
        circle.bindPopup(`<b>${d.type} Event</b><br>${d.description}<br>Impact Radius: ${d.radiusMeters}m`);
        disasterCirclesLayer.addLayer(circle);
    });
}

async function refreshActiveDisastersList() {
    try {
        const disasters = await API.listDisasters();
        activeDisasters = disasters;
        renderDisasterCirclesOnMap(disasters);

        const listEl = document.getElementById('active-disasters-list');
        if (!listEl) return;

        if (!disasters || disasters.length === 0) {
            listEl.innerHTML = '<p class="empty-msg">No active disasters.</p>';
            return;
        }

        listEl.innerHTML = disasters.map(d => `
            <div class="disaster-card">
                <div class="disaster-card-header">
                    <span>${d.type}</span>
                    <button class="btn-xs btn-danger" onclick="removeDisaster('${d.id}')">Remove</button>
                </div>
                <small>Impact Radius: ${d.radiusMeters}m | Road Blocked: ${d.blockRoads}</small>
            </div>
        `).join('');
    } catch (e) {
        console.error("Failed to refresh disasters list:", e);
    }
}

async function removeDisaster(id) {
    showLoading(true);
    try {
        await API.removeDisaster(id);
        await refreshActiveDisastersList();

        // Re-check live conditions for active route after disaster removal
        if (lastRouteResult && sourceNodeId && destNodeId) {
            await recomputeAndRefreshRoute();
        }

        showLoading(false);
    } catch (e) {
        console.error("Failed to remove disaster:", e);
        showLoading(false);
    }
}

async function clearAllDisasters() {
    showLoading(true);
    try {
        await API.clearAllDisasters();
        await refreshActiveDisastersList();

        // Route clears back to green / free-flow after all disasters removed
        if (lastRouteResult && sourceNodeId && destNodeId) {
            await recomputeAndRefreshRoute();
        }

        showLoading(false);
    } catch (e) {
        console.error("Failed to clear disasters:", e);
        showLoading(false);
    }
}
