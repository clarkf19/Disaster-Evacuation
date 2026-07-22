package com.mumbai.evacuation;

import com.mumbai.evacuation.algorithm.AStarEngine;
import com.mumbai.evacuation.algorithm.DijkstraEngine;
import com.mumbai.evacuation.loader.CsvGraphLoader;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.model.PathResult;

import java.io.File;
import java.util.List;

/**
 * Phase 1 Benchmark & Test Suite:
 * 1. Tests Dijkstra & A* on small synthetic sample graph.
 * 2. Benchmarks Dijkstra vs A* on real OpenStreetMap Mumbai Road Graph (8,951 nodes & 17,392 edges).
 * 3. Evaluates runtime (ms), nodes explored, path distance (km), and travel time (min) for major routes:
 *    - Route 1: Borivali -> Churchgate
 *    - Route 2: Dadar -> Andheri
 *    - Route 3: CST -> Goregaon
 */
public class Phase1BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("         MUMBAI DISASTER EVACUATION ENGINE - PHASE 1 BENCHMARK            ");
        System.out.println("==========================================================================");

        // 1. Sanity Test on Small Sample Graph
        System.out.println("\n--- STEP 1: Testing algorithms on Small Sample Graph ---");
        testSampleGraph();

        // 2. Load Real OSM Mumbai Graph
        String rootDir = System.getProperty("user.dir");
        File nodesCsv = new File(rootDir, "../data/mumbai_nodes.csv");
        File edgesCsv = new File(rootDir, "../data/mumbai_edges.csv");

        if (!nodesCsv.exists() || !edgesCsv.exists()) {
            // Try relative path if run from backend folder
            nodesCsv = new File("data/mumbai_nodes.csv");
            edgesCsv = new File("data/mumbai_edges.csv");
        }

        if (!nodesCsv.exists()) {
            nodesCsv = new File("C:/Users/Anushka/.gemini/antigravity/scratch/mumbai-evacuation-system/data/mumbai_nodes.csv");
            edgesCsv = new File("C:/Users/Anushka/.gemini/antigravity/scratch/mumbai-evacuation-system/data/mumbai_edges.csv");
        }

        System.out.println("\n--- STEP 2: Loading Real Mumbai OpenStreetMap Graph ---");
        System.out.println("Loading Nodes from: " + nodesCsv.getAbsolutePath());
        System.out.println("Loading Edges from: " + edgesCsv.getAbsolutePath());

        Graph mumbaiGraph;
        try {
            mumbaiGraph = CsvGraphLoader.loadGraphFromCsv(nodesCsv.getAbsolutePath(), edgesCsv.getAbsolutePath());
            System.out.println("SUCCESS: Loaded " + mumbaiGraph.getNodeCount() + " nodes and " + 
                               mumbaiGraph.getEdgeCount() + " edges into memory!");
        } catch (Exception e) {
            System.err.println("FAILED to load graph: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 3. Select Representative Landmarks for Mumbai Routes
        System.out.println("\n--- STEP 3: Benchmarking Dijkstra vs A* on Key Mumbai Corridors ---");

        // Find nearest nodes for key landmarks
        Node borivali = findNearestNode(mumbaiGraph, 19.2307, 72.8567, "Borivali");
        Node churchgate = findNearestNode(mumbaiGraph, 18.9322, 72.8264, "Churchgate");
        Node dadar = findNearestNode(mumbaiGraph, 19.0178, 72.8478, "Dadar");
        Node andheri = findNearestNode(mumbaiGraph, 19.1197, 72.8464, "Andheri");
        Node cst = findNearestNode(mumbaiGraph, 18.9401, 72.8351, "CST");
        Node goregaon = findNearestNode(mumbaiGraph, 19.1663, 72.8454, "Goregaon");

        benchmarkPair(mumbaiGraph, "Route 1: Borivali -> Churchgate", borivali, churchgate);
        benchmarkPair(mumbaiGraph, "Route 2: Dadar -> Andheri", dadar, andheri);
        benchmarkPair(mumbaiGraph, "Route 3: CST -> Goregaon", cst, goregaon);

        System.out.println("\n==========================================================================");
        System.out.println("                    PHASE 1 VERIFICATION COMPLETE                         ");
        System.out.println("==========================================================================");
    }

    private static void testSampleGraph() {
        Graph sample = new Graph();
        sample.addNode(new Node(1, 19.00, 72.80, "A"));
        sample.addNode(new Node(2, 19.01, 72.81, "B"));
        sample.addNode(new Node(3, 19.02, 72.82, "C"));
        sample.addNode(new Node(4, 19.03, 72.83, "D"));

        // Add sample edges
        sample.addEdge(new com.mumbai.evacuation.model.Edge(1, 1, 2, 1000.0, "primary", 50.0, 300));
        sample.addEdge(new com.mumbai.evacuation.model.Edge(2, 2, 4, 3000.0, "primary", 50.0, 300));
        sample.addEdge(new com.mumbai.evacuation.model.Edge(3, 1, 3, 1500.0, "secondary", 40.0, 200));
        sample.addEdge(new com.mumbai.evacuation.model.Edge(4, 3, 4, 1000.0, "secondary", 40.0, 200));

        DijkstraEngine dijkstra = new DijkstraEngine();
        AStarEngine aStar = new AStarEngine();

        PathResult resD = dijkstra.findShortestPath(sample, 1, 4);
        PathResult resA = aStar.findShortestPath(sample, 1, 4);

        System.out.println("Sample Graph Dijkstra Result: " + resD);
        System.out.println("Sample Graph A* Result:       " + resA);
        
        if (Math.abs(resD.getTotalTravelTimeSeconds() - resA.getTotalTravelTimeSeconds()) < 0.001) {
            System.out.println("PASS: Dijkstra and A* calculated identical optimal travel time on sample graph!");
        } else {
            System.err.println("FAIL: Travel time discrepancy on sample graph!");
        }
    }

    private static void benchmarkPair(Graph graph, String routeLabel, Node source, Node target) {
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" " + routeLabel);
        System.out.println(" Source: " + source.getName() + " (ID: " + source.getId() + ")");
        System.out.println(" Target: " + target.getName() + " (ID: " + target.getId() + ")");
        System.out.println("--------------------------------------------------------------------------");

        DijkstraEngine dijkstra = new DijkstraEngine();
        AStarEngine aStar = new AStarEngine();

        // Warmup runs
        dijkstra.findShortestPath(graph, source.getId(), target.getId());
        aStar.findShortestPath(graph, source.getId(), target.getId());

        // Measured runs (average over 5 runs)
        long totalDijkstraNs = 0;
        long totalAStarNs = 0;
        PathResult resD = null;
        PathResult resA = null;

        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            resD = dijkstra.findShortestPath(graph, source.getId(), target.getId());
            totalDijkstraNs += resD.getExecutionTimeNs();

            resA = aStar.findShortestPath(graph, source.getId(), target.getId());
            totalAStarNs += resA.getExecutionTimeNs();
        }

        double avgDijkstraMs = (totalDijkstraNs / (double) iterations) / 1_000_000.0;
        double avgAStarMs = (totalAStarNs / (double) iterations) / 1_000_000.0;

        System.out.printf("%-15s | %-12s | %-15s | %-12s | %-12s%n", "Algorithm", "Time (ms)", "Travel Time", "Distance", "Nodes Explored");
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("%-15s | %-12.3f | %-15.2f min | %-12.2f km | %-12d%n", 
                "Dijkstra", avgDijkstraMs, resD.getTotalTravelTimeMinutes(), resD.getTotalDistanceMeters() / 1000.0, resD.getNodesExplored());
        System.out.printf("%-15s | %-12.3f | %-15.2f min | %-12.2f km | %-12d%n", 
                "A* (Haversine)", avgAStarMs, resA.getTotalTravelTimeMinutes(), resA.getTotalDistanceMeters() / 1000.0, resA.getNodesExplored());

        double speedup = ((avgDijkstraMs - avgAStarMs) / avgDijkstraMs) * 100.0;
        double explorationReduction = ((resD.getNodesExplored() - resA.getNodesExplored()) / (double) resD.getNodesExplored()) * 100.0;
        System.out.printf("=> A* explored %.1f%% fewer nodes than Dijkstra!%n", explorationReduction);
    }

    private static Node findNearestNode(Graph graph, double targetLat, double targetLon, String name) {
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

        if (best != null) {
            best.setName(name);
        }
        return best;
    }
}
