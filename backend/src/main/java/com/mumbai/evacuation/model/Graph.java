package com.mumbai.evacuation.model;

import java.util.*;

/**
 * In-memory multimodal network: drivable roads, walk-only reverse directions of
 * one-way roads, and the suburban railway (stations, train links and station
 * access links).
 *
 * The graph is built once at startup and never mutated afterwards, so plain
 * collections are safe to share between request threads. A uniform grid index
 * over road nodes makes nearest-node lookups O(1) on average.
 */
public class Graph {
    private static final double CELL_DEG = 0.005; // ~550 m
    private static final int MAX_SEARCH_RING = 400;

    static final long REVERSE_EDGE_ID_OFFSET = 1_000_000_000L;
    static final long RAIL_EDGE_ID_OFFSET = 2_000_000_000L;
    static final long TRANSFER_EDGE_ID_OFFSET = 3_000_000_000L;
    /** Stations can be linked to a road node at most this far away (m). */
    static final double MAX_STATION_ACCESS_METERS = 600;
    /** Platform node ids count down from here (stations use -1,000,000 - station id). */
    static final long PLATFORM_ID_BASE = -5_000_000L;

    private final Map<Long, Node> nodes = new HashMap<>();
    private final Map<Long, List<Edge>> adjacencyList = new HashMap<>();
    private final Map<Long, Edge> edgesById = new HashMap<>();
    private final Map<Long, List<Node>> grid = new HashMap<>();
    private final List<Node> stations = new ArrayList<>();
    /** Platform node id -> its line; one platform per station and line. */
    private final Map<Long, String> platformLines = new HashMap<>();
    /** Station (concourse) node id -> the lines that call there. */
    private final Map<Long, Set<String>> stationLines = new HashMap<>();
    private final EnumMap<Edge.Kind, Integer> edgeCounts = new EnumMap<>(Edge.Kind.class);
    private double maxRoadSpeedKmH = 10.0;
    private double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
    private double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

    /** Adds a road intersection (indexed for snapping). */
    public void addNode(Node node) {
        nodes.put(node.getId(), node);
        adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
        if (node.isStation()) {
            stations.add(node);
            return;
        }
        grid.computeIfAbsent(cellKey(cellX(node.getLongitude()), cellY(node.getLatitude())), k -> new ArrayList<>()).add(node);
        minLat = Math.min(minLat, node.getLatitude());
        maxLat = Math.max(maxLat, node.getLatitude());
        minLon = Math.min(minLon, node.getLongitude());
        maxLon = Math.max(maxLon, node.getLongitude());
    }

    public void addEdge(Edge edge) {
        edgesById.put(edge.getId(), edge);
        adjacencyList.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
        edgeCounts.merge(edge.getKind(), 1, Integer::sum);
        if (edge.getKind() == Edge.Kind.ROAD) maxRoadSpeedKmH = Math.max(maxRoadSpeedKmH, edge.getSpeedLimitKmH());
    }

    /**
     * Adds a walk-only reverse edge for every one-way road (pedestrians may walk
     * against the traffic direction). Call once after all roads are loaded.
     */
    public void addWalkingReverseEdges() {
        List<Edge> roads = edgesById.values().stream().filter(e -> e.getKind() == Edge.Kind.ROAD).toList();
        for (Edge e : roads) {
            boolean hasOpposite = getOutgoingEdges(e.getTargetNodeId()).stream()
                    .anyMatch(o -> o.getKind() == Edge.Kind.ROAD && o.getTargetNodeId() == e.getSourceNodeId());
            if (!hasOpposite) {
                addEdge(Edge.reverseForWalking(REVERSE_EDGE_ID_OFFSET + e.getId(), e));
            }
        }
    }

    /**
     * Adds suburban stations and train links.
     *
     * Each station becomes a concourse node, linked to its nearest road node by a short walk,
     * plus one platform node per line calling there. Getting on a train means walking from the
     * concourse to that line's platform and waiting for the next train; getting off takes a minute.
     * Trains run between platforms of the same line, so changing lines costs a real wait in the
     * search (get off + wait again) and a ride stays on one line where lines share track.
     * Stations with no road node within 600 m are skipped.
     *
     * @param links pairs of stations with the line name and length; trains run both ways
     */
    public void addRailNetwork(List<Node> stationNodes, List<RailLink> links) {
        Map<Long, Node> connected = new HashMap<>();
        long transferId = TRANSFER_EDGE_ID_OFFSET;
        for (Node station : stationNodes) {
            Node road = findNearestNode(station.getLatitude(), station.getLongitude());
            if (road == null) continue;
            double d = GeoUtils.haversineMeters(station.getLatitude(), station.getLongitude(), road.getLatitude(), road.getLongitude());
            if (d > MAX_STATION_ACCESS_METERS) continue;
            addNode(station);
            connected.put(station.getId(), station);
            double walkSeconds = d / (TravelMode.WALK_KMH * 1000.0 / 3600.0);
            addEdge(Edge.transfer(transferId++, road.getId(), station.getId(), d, walkSeconds));
            addEdge(Edge.transfer(transferId++, station.getId(), road.getId(), d, walkSeconds));
        }

        Map<String, Long> platforms = new HashMap<>(); // "stationId|line" -> platform id
        long platformId = PLATFORM_ID_BASE;
        long railId = RAIL_EDGE_ID_OFFSET;
        for (RailLink link : links) {
            Node from = connected.get(link.fromStationId());
            Node to = connected.get(link.toStationId());
            if (from == null || to == null) continue;
            long[] ends = new long[2];
            Node[] stationsOfLink = {from, to};
            for (int i = 0; i < 2; i++) {
                Node st = stationsOfLink[i];
                String key = st.getId() + "|" + link.line();
                Long existing = platforms.get(key);
                if (existing == null) {
                    existing = platformId--;
                    platforms.put(key, existing);
                    addPlatform(new Node(existing, st.getLatitude(), st.getLongitude(),
                            st.getElevationM(), st.getName(), true), link.line());
                    stationLines.computeIfAbsent(st.getId(), k -> new TreeSet<>()).add(link.line());
                    addEdge(Edge.transfer(transferId++, st.getId(), existing, 0, TravelMode.BOARDING_WAIT_SECONDS));
                    addEdge(Edge.transfer(transferId++, existing, st.getId(), 0, TravelMode.ALIGHTING_SECONDS));
                }
                ends[i] = existing;
            }
            addEdge(Edge.rail(railId++, ends[0], ends[1], link.meters(), link.line(),
                    TravelMode.RAIL_KMH, TravelMode.RAIL_DWELL_SECONDS, TravelMode.RAIL_CAPACITY_PER_HOUR));
            addEdge(Edge.rail(railId++, ends[1], ends[0], link.meters(), link.line(),
                    TravelMode.RAIL_KMH, TravelMode.RAIL_DWELL_SECONDS, TravelMode.RAIL_CAPACITY_PER_HOUR));
        }
    }

    /** A platform: part of the graph, but neither indexed for snapping nor listed as a station. */
    private void addPlatform(Node platform, String line) {
        nodes.put(platform.getId(), platform);
        adjacencyList.putIfAbsent(platform.getId(), new ArrayList<>());
        platformLines.put(platform.getId(), line);
    }

    /** True for a station's per-line platform node. */
    public boolean isPlatform(long nodeId) { return platformLines.containsKey(nodeId); }

    /** Lines calling at a station (concourse) node, sorted; empty if none. */
    public Set<String> getStationLines(long stationId) {
        return stationLines.getOrDefault(stationId, Collections.emptySet());
    }

    public record RailLink(long fromStationId, long toStationId, String line, double meters) {}

    public Node getNode(long nodeId) { return nodes.get(nodeId); }
    public Edge getEdge(long edgeId) { return edgesById.get(edgeId); }
    public Collection<Node> getAllNodes() { return Collections.unmodifiableCollection(nodes.values()); }
    public Collection<Edge> getAllEdges() { return Collections.unmodifiableCollection(edgesById.values()); }
    public List<Node> getStations() { return Collections.unmodifiableList(stations); }
    public List<Edge> getOutgoingEdges(long nodeId) { return adjacencyList.getOrDefault(nodeId, Collections.emptyList()); }
    public int getNodeCount() { return nodes.size(); }
    public int getEdgeCount() { return edgesById.size(); }
    public int getRoadNodeCount() { return nodes.size() - stations.size() - platformLines.size(); }
    public int getEdgeCount(Edge.Kind kind) { return edgeCounts.getOrDefault(kind, 0); }

    /** Fastest road speed limit; keeps the driving A* heuristic admissible. */
    public double getMaxRoadSpeedKmH() { return maxRoadSpeedKmH; }

    /** [minLat, minLon, maxLat, maxLon] of the road network, or null when empty. */
    public double[] getBounds() {
        return minLat == Double.MAX_VALUE ? null : new double[]{minLat, minLon, maxLat, maxLon};
    }

    /** Nearest ROAD node by true (haversine) distance, or null when there are none. */
    public Node findNearestNode(double lat, double lon) {
        if (grid.isEmpty()) return null;
        int cx = cellX(lon), cy = cellY(lat);
        Node best = null;
        double bestDist = Double.MAX_VALUE;
        int foundAtRing = -1;
        for (int r = 0; r <= MAX_SEARCH_RING; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    List<Node> cell = grid.get(cellKey(cx + dx, cy + dy));
                    if (cell == null) continue;
                    for (Node n : cell) {
                        double d = GeoUtils.haversineMeters(lat, lon, n.getLatitude(), n.getLongitude());
                        if (d < bestDist) { bestDist = d; best = n; }
                    }
                }
            }
            if (best != null && foundAtRing < 0) foundAtRing = r;
            // One extra ring: a node in a diagonal neighbour can be closer than the first hit.
            if (foundAtRing >= 0 && r >= foundAtRing + 1) break;
        }
        return best;
    }

    private static int cellX(double lon) { return (int) Math.floor(lon / CELL_DEG); }
    private static int cellY(double lat) { return (int) Math.floor(lat / CELL_DEG); }
    private static long cellKey(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
}
