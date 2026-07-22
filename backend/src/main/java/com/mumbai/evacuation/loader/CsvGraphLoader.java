package com.mumbai.evacuation.loader;

import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Loads CSV exported Mumbai graph data into the in-memory Graph adjacency structure.
 */
public class CsvGraphLoader {

    public static Graph loadGraphFromCsv(String nodesCsvPath, String edgesCsvPath) throws IOException {
        Graph graph = new Graph();
        
        File nodesFile = new File(nodesCsvPath);
        File edgesFile = new File(edgesCsvPath);

        if (!nodesFile.exists() || !edgesFile.exists()) {
            throw new IllegalArgumentException("CSV file(s) not found at: " + nodesCsvPath + ", " + edgesCsvPath);
        }

        // Read Nodes
        try (BufferedReader reader = new BufferedReader(new FileReader(nodesFile))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = line.split(",");
                long id = Long.parseLong(tokens[0].trim());
                double lat = Double.parseDouble(tokens[1].trim());
                double lon = Double.parseDouble(tokens[2].trim());
                String name = tokens.length > 3 ? tokens[3].trim() : null;

                graph.addNode(new Node(id, lat, lon, name));
            }
        }

        // Read Edges
        try (BufferedReader reader = new BufferedReader(new FileReader(edgesFile))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = line.split(",");
                long id = Long.parseLong(tokens[0].trim());
                long source = Long.parseLong(tokens[1].trim());
                long destination = Long.parseLong(tokens[2].trim());
                double distance = Double.parseDouble(tokens[3].trim());
                String roadType = tokens[4].trim();
                double speedLimit = Double.parseDouble(tokens[5].trim());
                int capacity = Integer.parseInt(tokens[6].trim());

                graph.addEdge(new Edge(id, source, destination, distance, roadType, speedLimit, capacity));
            }
        }

        return graph;
    }
}
