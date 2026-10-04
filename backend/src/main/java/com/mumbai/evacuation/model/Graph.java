package com.mumbai.evacuation.model;

import java.util.*;

/**
 * In-memory adjacency-list road graph.
 *
 * The graph is built once at startup and never mutated afterwards, so plain
 * collections are safe to share between request threads. A uniform grid index
 * makes nearest-node lookups O(1) on average instead of a scan over every node.
 */
public class Graph {
    private static final double CELL_DEG = 0.005; // ~550 m
    private static final int MAX_SEARCH_RING = 400;

    private final Map<Long, Node> nodes = new HashMap<>();
    private final Map<Long, List<Edge>> adjacencyList = new HashMap<>();
    private final Map<Long, Edge> edgesById = new HashMap<>();
    private final Map<Long, List<Node>> grid = new HashMap<>();
    private double maxSpeedKmH = 10.0;
    private double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
    private double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

    public void addNode(Node node) {
        nodes.put(node.getId(), node);
        adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
        grid.computeIfAbsent(cellKey(cellX(node.getLongitude()), cellY(node.getLatitude())), k -> new ArrayList<>()).add(node);
        minLat = Math.min(minLat, node.getLatitude());
        maxLat = Math.max(maxLat, node.getLatitude());
        minLon = Math.min(minLon, node.getLongitude());
        maxLon = Math.max(maxLon, node.getLongitude());
    }

    public void addEdge(Edge edge) {
        edgesById.put(edge.getId(), edge);
        adjacencyList.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
        maxSpeedKmH = Math.max(maxSpeedKmH, edge.getSpeedLimitKmH());
    }

    public Node getNode(long nodeId) { return nodes.get(nodeId); }
    public Edge getEdge(long edgeId) { return edgesById.get(edgeId); }
    public Collection<Node> getAllNodes() { return Collections.unmodifiableCollection(nodes.values()); }
    public Collection<Edge> getAllEdges() { return Collections.unmodifiableCollection(edgesById.values()); }
    public List<Edge> getOutgoingEdges(long nodeId) { return adjacencyList.getOrDefault(nodeId, Collections.emptyList()); }
    public int getNodeCount() { return nodes.size(); }
    public int getEdgeCount() { return edgesById.size(); }

    /** Fastest speed limit in the network; keeps the A* heuristic admissible. */
    public double getMaxSpeedKmH() { return maxSpeedKmH; }

    /** [minLat, minLon, maxLat, maxLon] of the loaded network, or null when empty. */
    public double[] getBounds() {
        return nodes.isEmpty() ? null : new double[]{minLat, minLon, maxLat, maxLon};
    }

    /** Nearest node by true (haversine) distance, or null when the graph is empty. */
    public Node findNearestNode(double lat, double lon) {
        if (nodes.isEmpty()) return null;
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
