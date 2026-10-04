package com.mumbai.evacuation.loader;

import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the OSM-derived CSV road network into an in-memory {@link Graph}.
 *
 * nodes CSV: id,latitude,longitude[,name]
 * edges CSV: id,source,destination,distance_meters,road_type,speed_limit_kmh,capacity
 */
public final class CsvGraphLoader {

    private CsvGraphLoader() {}

    public static Graph load(InputStream nodesCsv, InputStream edgesCsv) throws IOException {
        Graph graph = new Graph();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodesCsv, StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                String[] t = line.split(",");
                try {
                    String name = t.length > 3 && !t[3].isBlank() ? t[3].trim() : null;
                    graph.addNode(new Node(Long.parseLong(t[0].trim()), Double.parseDouble(t[1].trim()),
                            Double.parseDouble(t[2].trim()), name));
                } catch (RuntimeException e) {
                    throw new IOException("Malformed nodes CSV at line " + lineNo + ": " + line, e);
                }
            }
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(edgesCsv, StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                String[] t = line.split(",");
                try {
                    long source = Long.parseLong(t[1].trim());
                    long target = Long.parseLong(t[2].trim());
                    if (graph.getNode(source) == null || graph.getNode(target) == null) {
                        throw new IllegalArgumentException("edge references unknown node");
                    }
                    graph.addEdge(new Edge(Long.parseLong(t[0].trim()), source, target,
                            Double.parseDouble(t[3].trim()), t[4].trim(),
                            Double.parseDouble(t[5].trim()), Integer.parseInt(t[6].trim())));
                } catch (RuntimeException e) {
                    throw new IOException("Malformed edges CSV at line " + lineNo + ": " + line, e);
                }
            }
        }
        return graph;
    }
}
