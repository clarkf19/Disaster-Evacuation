package com.mumbai.evacuation.service;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.model.PathResult;
import com.mumbai.evacuation.model.Shelter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing Mumbai Evacuation Shelters.
 * 
 * Design Decision Rationale:
 * - Pre-loads 22 designated high-capacity evacuation shelters across MMR (Wankhede Stadium, 
 *   Bandra Kurla Complex Ground, Shivaji Park, Cooperage Ground, MMRDA Grounds Powai, etc.).
 * - Maps each shelter to its nearest OpenStreetMap road network node.
 * - Provides capacity-aware routing fallback: if the primary nearest shelter is at full capacity,
 *   automatically selects the next closest available shelter with sufficient remaining capacity.
 */
public class ShelterService {

    private final Map<Long, Shelter> shelterMap = new ConcurrentHashMap<>();

    public void initializeMumbaiShelters(Graph graph) {
        shelterMap.clear();

        List<ShelterSeed> seeds = Arrays.asList(
            new ShelterSeed(1, "Wankhede Stadium (Churchgate)", 18.9389, 72.8258, 25000),
            new ShelterSeed(2, "Brabourne Stadium (Marine Drive)", 18.9328, 72.8242, 20000),
            new ShelterSeed(3, "Azad Maidan Ground (CST)", 18.9412, 72.8315, 15000),
            new ShelterSeed(4, "Cross Maidan (Churchgate)", 18.9378, 72.8302, 12000),
            new ShelterSeed(5, "Oval Maidan (Colaba)", 18.9295, 72.8288, 10000),
            new ShelterSeed(6, "Cooprage Football Ground (Colaba)", 18.9215, 72.8270, 8000),
            new ShelterSeed(7, "Byculla Zoo Grounds (Byculla)", 18.9780, 72.8350, 15000),
            new ShelterSeed(8, "Shivaji Park (Dadar)", 19.0269, 72.8381, 30000),
            new ShelterSeed(9, "Worli Seaface Sports Complex", 19.0125, 72.8160, 10000),
            new ShelterSeed(10, "Mahim Nature Park", 19.0435, 72.8552, 12000),
            new ShelterSeed(11, "BKC Exhibition Ground (Bandra E)", 19.0665, 72.8680, 35000),
            new ShelterSeed(12, "MIG Cricket Club Ground (Bandra W)", 19.0580, 72.8320, 10000),
            new ShelterSeed(13, "Sion Fort Grounds (Sion)", 19.0420, 72.8630, 8000),
            new ShelterSeed(14, "Kurla Sports Complex Ground", 19.0680, 72.8820, 15000),
            new ShelterSeed(15, "Chembur Gymkhana (Chembur)", 19.0610, 72.9010, 12000),
            new ShelterSeed(16, "Andheri Sports Complex (Andheri W)", 19.1310, 72.8350, 25000),
            new ShelterSeed(17, "JVPD Grounds (Juhu)", 19.1080, 72.8270, 15000),
            new ShelterSeed(18, "Goregaon Sports Club (Goregaon W)", 19.1680, 72.8390, 20000),
            new ShelterSeed(19, "IIT Bombay Gymnasium Grounds (Powai)", 19.1330, 72.9150, 18000),
            new ShelterSeed(20, "Vikhroli Ground", 19.1120, 72.9300, 10000),
            new ShelterSeed(21, "Borivali National Park Arena", 19.2310, 72.8620, 25000),
            new ShelterSeed(22, "Thane Stadium Grounds", 19.1880, 72.9650, 20000)
        );

        for (ShelterSeed seed : seeds) {
            Node nearest = findNearestNode(graph, seed.lat, seed.lon);
            long nearestId = nearest != null ? nearest.getId() : -1;
            Shelter shelter = new Shelter(seed.id, seed.name, seed.lat, seed.lon, nearestId, seed.capacity);
            shelterMap.put(shelter.getId(), shelter);
        }
    }

    public Collection<Shelter> getAllShelters() {
        return shelterMap.values();
    }

    public Shelter getShelter(long id) {
        return shelterMap.get(id);
    }

    /**
     * Finds the nearest available shelter with capacity from a given source node.
     */
    public ShelterAssignmentResult findNearestAvailableShelter(Graph graph, long sourceNodeId, int evacueeCount) {
        Node sourceNode = graph.getNode(sourceNodeId);
        if (sourceNode == null) return null;

        AStarEngine aStar = new AStarEngine();
        Shelter bestShelter = null;
        PathResult bestPath = null;
        double minTravelTime = Double.MAX_VALUE;

        for (Shelter shelter : shelterMap.values()) {
            if (shelter.getRemainingCapacity() < evacueeCount) {
                continue; // Skip full shelters
            }

            PathResult path = aStar.findShortestPath(graph, sourceNodeId, shelter.getNearestNodeId());
            if (path.isPathFound() && path.getTotalTravelTimeSeconds() < minTravelTime) {
                minTravelTime = path.getTotalTravelTimeSeconds();
                bestShelter = shelter;
                bestPath = path;
            }
        }

        if (bestShelter != null && bestPath != null) {
            return new ShelterAssignmentResult(bestShelter, bestPath);
        }
        return null;
    }

    private Node findNearestNode(Graph graph, double targetLat, double targetLon) {
        Node best = null;
        double minDistance = Double.MAX_VALUE;
        for (Node node : graph.getAllNodes()) {
            double dlat = node.getLatitude() - targetLat;
            double dlon = node.getLongitude() - targetLon;
            double distSq = dlat * dlat + dlon * dlon;
            if (distSq < minDistance) {
                minDistance = distSq;
                best = node;
            }
        }
        return best;
    }

    private static class ShelterSeed {
        final long id;
        final String name;
        final double lat;
        final double lon;
        final int capacity;

        ShelterSeed(long id, String name, double lat, double lon, int capacity) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.capacity = capacity;
        }
    }

    public static class ShelterAssignmentResult {
        public final Shelter shelter;
        public final PathResult pathResult;

        public ShelterAssignmentResult(Shelter shelter, PathResult pathResult) {
            this.shelter = shelter;
            this.pathResult = pathResult;
        }
    }
}
