// api.js — all fetch calls to the Spring Boot backend
const API_BASE = 'http://localhost:8080/api';

async function apiGet(path) {
    const res = await fetch(API_BASE + path);
    if (!res.ok) throw new Error(`GET ${path} → ${res.status}`);
    return res.json();
}

async function apiPost(path, body) {
    const res = await fetch(API_BASE + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`POST ${path} → ${res.status}`);
    return res.json();
}

async function apiDelete(path) {
    const res = await fetch(API_BASE + path, { method: 'DELETE' });
    if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}`);
    return res.json();
}

const API = {
    graphStats: ()             => apiGet('/graph/stats'),
    nodes:      ()             => apiGet('/nodes'),
    nearest:    (lat, lon)     => apiGet(`/nearest?lat=${lat}&lon=${lon}`),
    route:      (src, dst, al) => apiPost('/route', { sourceNodeId: src, targetNodeId: dst, algorithm: al }),
    shelters:   ()             => apiGet('/shelters'),
    disasters:  ()             => apiGet('/disasters'),
    addDisaster:(body)         => apiPost('/disasters', body),
    removeDisaster:(id)        => apiDelete(`/disasters/${id}`),
    clearDisasters:()          => apiDelete('/disasters'),
};
