package com.mumbai.evacuation.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Adjacency List Graph Engine for Mumbai Road Network.
 * 
 * Design Decision Rationale:
 * 1. Fast Memory Lookup O(1):
 *    - Nodes stored in Map<Long, Node> indexed by Node ID.
 *    - Outgoing edges stored in Map<Long, List<Edge>> representing an adjacency list representation.
 * 2. Thread Safety:
 *    - ConcurrentHashMap used for core node and adjacency structures to support safe parallel pathfinding queries.
 */
public class Graph {
    private final Map<Long, Node> nodes = new ConcurrentHashMap<>();
    private final Map<Long, List<Edge>> adjacencyList = new ConcurrentHashMap<>();
    private final Map<Long, Edge> edgesById = new ConcurrentHashMap<>();

    public void addNode(Node node) {
        nodes.put(node.getId(), node);
        adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
    }

    public void addEdge(Edge edge) {
        edgesById.put(edge.getId(), edge);
        adjacencyList.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
    }

    public Node getNode(long nodeId) {
        return nodes.get(nodeId);
    }

    public Edge getEdge(long edgeId) {
        return edgesById.get(edgeId);
    }

    public Collection<Node> getAllNodes() {
        return nodes.values();
    }

    public Collection<Edge> getAllEdges() {
        return edgesById.values();
    }

    public List<Edge> getOutgoingEdges(long nodeId) {
        return adjacencyList.getOrDefault(nodeId, Collections.emptyList());
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edgesById.size();
    }

    public void clear() {
        nodes.clear();
        adjacencyList.clear();
        edgesById.clear();
    }
}
