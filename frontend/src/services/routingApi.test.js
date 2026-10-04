import { describe, expect, it } from 'vitest';
import { factorToCongestion, normalizeResponse, coordinateLabel } from './routingApi';

describe('factorToCongestion', () => {
  it('maps factors to traffic classes', () => {
    expect(factorToCongestion(1.0)).toBe('clear');
    expect(factorToCongestion(1.3)).toBe('slow');
    expect(factorToCongestion(1.7)).toBe('moderate');
    expect(factorToCongestion(2.0)).toBe('heavy'); // hazard egress penalty
    expect(factorToCongestion(3.5)).toBe('heavy');
  });
});

describe('normalizeResponse', () => {
  it('keeps route source, warnings and multi-point segments', () => {
    const r = normalizeResponse({
      pathFound: true,
      routeSource: 'ROAD_GRAPH',
      distanceKm: 4.2,
      liveTravelTimeMinutes: 9,
      freeFlowTravelTimeMinutes: 8,
      delayMinutes: 1,
      liveStatus: 'DISASTER_BYPASS',
      advisoryMessage: 'Diverted',
      warnings: ['dest unsafe'],
      routeCoordinates: [[19, 72.8], [19.01, 72.81]],
      segments: [
        { points: [[19, 72.8], [19.01, 72.81]], congestionFactor: 2.0 },
        { points: [[19.01, 72.81]], congestionFactor: 1.0 }, // degenerate, dropped
      ],
    });
    expect(r.found).toBe(true);
    expect(r.source).toBe('ROAD_GRAPH');
    expect(r.warnings).toEqual(['dest unsafe']);
    expect(r.segments).toHaveLength(1);
    expect(r.segments[0].congestion).toBe('heavy');
  });

  it('reports an unpassable route as not found', () => {
    const r = normalizeResponse({ pathFound: false, liveStatus: 'UNPASSABLE', advisoryMessage: 'blocked' });
    expect(r.found).toBe(false);
    expect(r.points).toEqual([]);
    expect(r.segments).toEqual([]);
  });
});

describe('coordinateLabel', () => {
  it('formats to 4 decimals', () => {
    expect(coordinateLabel(19.123456, 72.8)).toBe('19.1235, 72.8000');
  });
});
