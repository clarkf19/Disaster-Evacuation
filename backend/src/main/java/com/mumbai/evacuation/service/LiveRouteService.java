package com.mumbai.evacuation.service;

import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.HazardOverlay;
import com.mumbai.evacuation.dto.LiveRouteRequest;
import com.mumbai.evacuation.dto.LiveRouteResponse;
import com.mumbai.evacuation.dto.RouteResponse;
import com.mumbai.evacuation.model.GeoUtils;
import com.mumbai.evacuation.model.Shelter;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Route planner used by the UI. Guarantees that the returned route respects
 * the active hazard zones:
 *
 * 1. If TomTom is configured and neither endpoint is inside a hazard zone, ask
 *    TomTom for a live-traffic route with every hazard zone as an avoid-area,
 *    then verify server-side that no point of the returned geometry enters a zone.
 * 2. Otherwise (no key, TomTom failure, verification failed, or the user must
 *    first get OUT of a zone) use our own hazard-aware A* on the road graph.
 *
 * There is no straight-line fallback: if no passable route exists the response
 * says so instead of drawing a line across water or through a hazard.
 */
@Service
public class LiveRouteService {

    private final GraphService graphService;
    private final TomTomService tomTomService;
    private final ShelterService shelterService;

    public LiveRouteService(GraphService graphService, TomTomService tomTomService, ShelterService shelterService) {
        this.graphService = graphService;
        this.tomTomService = tomTomService;
        this.shelterService = shelterService;
    }

    public LiveRouteResponse route(LiveRouteRequest req) {
        HazardOverlay overlay = graphService.getHazardOverlay();
        List<DisasterEvent> disasters = overlay.getDisasters();
        boolean originInZone = !overlay.disastersAt(req.fromLat(), req.fromLon()).isEmpty();
        boolean destInZone = !overlay.disastersAt(req.toLat(), req.toLon()).isEmpty();

        List<String> warnings = new ArrayList<>();
        if (destInZone) {
            warnings.add("Your destination is inside an active hazard zone. Choose a destination outside the zone if you can.");
        }
        shelterService.getAllShelters().stream()
                .filter(s -> GeoUtils.haversineMeters(s.getLatitude(), s.getLongitude(), req.toLat(), req.toLon()) < 150)
                .filter(overlay::isShelterUnsafe)
                .findFirst()
                .map(Shelter::getName)
                .ifPresent(name -> warnings.add(name + " is currently unsafe (inside a hazard zone or flood-prone during a flood)."));

        if (tomTomService.isConfigured() && !originInZone && !destInZone && disasters.size() <= TomTomService.MAX_AVOID_AREAS) {
            Optional<TomTomService.TomTomRoute> live = tomTomService.route(req.fromLat(), req.fromLon(), req.toLat(), req.toLon(), disasters);
            if (live.isPresent() && !live.get().points().isEmpty() && avoidsAllZones(live.get().points(), disasters)) {
                LiveRouteResponse response = fromTomTom(live.get(), !disasters.isEmpty());
                response.getWarnings().addAll(warnings);
                return response;
            }
        }

        LiveRouteResponse response = fromGraph(graphService.computeRoute(req.fromLat(), req.fromLon(), req.toLat(), req.toLon()));
        if (tomTomService.isConfigured() && response.isPathFound()) {
            warnings.add("Live traffic unavailable for this route — times assume free-flowing traffic.");
        }
        response.getWarnings().addAll(warnings);
        return response;
    }

    static boolean avoidsAllZones(List<double[]> points, List<DisasterEvent> disasters) {
        for (int i = 0; i + 1 < points.size(); i++) {
            double[] a = points.get(i), b = points.get(i + 1);
            for (DisasterEvent d : disasters) {
                if (GeoUtils.pointToSegmentMeters(d.getCenterLatitude(), d.getCenterLongitude(), a[0], a[1], b[0], b[1])
                        <= d.getAffectedRadiusMeters()) {
                    return false;
                }
            }
        }
        return true;
    }

    private LiveRouteResponse fromTomTom(TomTomService.TomTomRoute route, boolean hazardsActive) {
        LiveRouteResponse resp = new LiveRouteResponse();
        resp.setPathFound(true);
        resp.setRouteSource("TOMTOM_LIVE");
        resp.setDistanceKm(route.lengthMeters() / 1000.0);
        resp.setLiveTravelTimeMinutes((int) Math.max(1, Math.round(route.travelSeconds() / 60.0)));
        resp.setFreeFlowTravelTimeMinutes((int) Math.max(1, Math.round(route.noTrafficSeconds() / 60.0)));
        int delayMins = (int) Math.max(0, Math.round(route.delaySeconds() / 60.0));
        resp.setDelayMinutes(delayMins);
        resp.setRouteCoordinates(route.points());
        resp.setSegments(trafficSegments(route));

        String status;
        if (route.delaySeconds() >= 600) status = "HEAVY_CONGESTION";
        else if (route.delaySeconds() >= 180) status = "MODERATE_TRAFFIC";
        else if (route.delaySeconds() >= 60) status = "SLOW_TRAFFIC";
        else status = "CLEAR";
        String advisory = switch (status) {
            case "HEAVY_CONGESTION" -> String.format(Locale.US, "Heavy live traffic: +%d min delay on the fastest corridor.", delayMins);
            case "MODERATE_TRAFFIC" -> String.format(Locale.US, "Moderate traffic: +%d min delay.", delayMins);
            case "SLOW_TRAFFIC" -> String.format(Locale.US, "Slight slowdown (+%d min) over %.1f km.", delayMins, resp.getDistanceKm());
            default -> String.format(Locale.US, "Traffic is flowing freely over %.1f km.", resp.getDistanceKm());
        };
        if (hazardsActive) {
            advisory += " Route verified to stay clear of all active hazard zones.";
        }
        resp.setLiveStatus(status);
        resp.setAdvisoryMessage(advisory);
        return resp;
    }

    private static List<LiveRouteResponse.SegmentInfo> trafficSegments(TomTomService.TomTomRoute route) {
        List<double[]> points = route.points();
        List<LiveRouteResponse.SegmentInfo> segments = new ArrayList<>();
        if (points.size() < 2 || route.trafficSections().isEmpty()) {
            segments.add(new LiveRouteResponse.SegmentInfo(points, 1.0));
            return segments;
        }
        int cursor = 0;
        int last = points.size() - 1;
        for (TomTomService.TrafficSection sec : route.trafficSections()) {
            int start = Math.max(cursor, Math.min(sec.startPointIndex(), last));
            int end = Math.max(start, Math.min(sec.endPointIndex(), last));
            if (cursor < start) segments.add(new LiveRouteResponse.SegmentInfo(List.copyOf(points.subList(cursor, start + 1)), 1.0));
            int mag = sec.magnitudeOfDelay();
            double factor = mag >= 3 ? 2.5 : mag >= 2 ? 1.7 : mag >= 1 ? 1.3 : 1.0;
            if (end > start) segments.add(new LiveRouteResponse.SegmentInfo(List.copyOf(points.subList(start, end + 1)), factor));
            cursor = end;
        }
        if (cursor < last) segments.add(new LiveRouteResponse.SegmentInfo(List.copyOf(points.subList(cursor, points.size())), 1.0));
        return segments;
    }

    private static LiveRouteResponse fromGraph(RouteResponse graphRoute) {
        LiveRouteResponse resp = new LiveRouteResponse();
        resp.setRouteSource("ROAD_GRAPH");
        resp.setPathFound(graphRoute.isPathFound());
        resp.setLiveStatus(graphRoute.getLiveRouteStatus());
        resp.setAdvisoryMessage(graphRoute.getLiveAdvisoryMessage());
        if (!graphRoute.isPathFound()) return resp;

        resp.setDistanceKm(graphRoute.getTotalDistanceKm());
        resp.setLiveTravelTimeMinutes((int) Math.max(1, Math.round(graphRoute.getTotalTravelTimeMinutes())));
        resp.setFreeFlowTravelTimeMinutes((int) Math.max(1, Math.round(graphRoute.getFreeFlowTravelTimeMinutes())));
        resp.setDelayMinutes((int) Math.round(graphRoute.getCongestionDelayMinutes()));
        resp.setRouteCoordinates(graphRoute.getRawCoordinates());

        // Merge consecutive edges with the same hazard factor into one coloured segment.
        List<LiveRouteResponse.SegmentInfo> segments = new ArrayList<>();
        List<double[]> current = new ArrayList<>();
        double currentFactor = Double.NaN;
        for (RouteResponse.SegmentDetail s : graphRoute.getSegmentDetails()) {
            if (current.isEmpty() || s.congestionFactor() != currentFactor) {
                if (!current.isEmpty()) segments.add(new LiveRouteResponse.SegmentInfo(current, currentFactor));
                current = new ArrayList<>();
                current.add(new double[]{s.startLat(), s.startLon()});
                currentFactor = s.congestionFactor();
            }
            current.add(new double[]{s.endLat(), s.endLon()});
        }
        if (!current.isEmpty()) segments.add(new LiveRouteResponse.SegmentInfo(current, currentFactor));
        resp.setSegments(segments);

        if (graphRoute.getSourceSnapMeters() > 300) {
            resp.getWarnings().add(String.format(Locale.US,
                    "Your start point is %.0f m from the nearest mapped main road; the route begins there.", graphRoute.getSourceSnapMeters()));
        }
        return resp;
    }
}
