package com.mumbai.evacuation.loader;

import com.mumbai.evacuation.model.Edge;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads the CSV datasets produced by scripts/build_datasets.py into a {@link Graph}.
 *
 * nodes:    id,latitude,longitude[,elevation_m]
 * edges:    id,source,destination,distance_meters,road_type,speed_limit_kmh,capacity
 * stations: id,name,latitude,longitude,lines,elevation_m      (optional)
 * links:    from_station,to_station,line,distance_meters       (optional)
 *
 * Columns are looked up by header name, so optional columns may be absent.
 */
public final class CsvGraphLoader {

    /** Station ids are mapped below zero so they can never clash with OSM node ids. */
    public static final long STATION_ID_BASE = -1_000_000L;

    private CsvGraphLoader() {}

    public static Graph load(InputStream nodesCsv, InputStream edgesCsv) throws IOException {
        return load(nodesCsv, edgesCsv, null, null);
    }

    public static Graph load(InputStream nodesCsv, InputStream edgesCsv,
                             InputStream stationsCsv, InputStream linksCsv) throws IOException {
        Graph graph = new Graph();

        for (Row r : read(nodesCsv, "nodes")) {
            graph.addNode(new Node(r.longValue("id"), r.doubleValue("latitude"), r.doubleValue("longitude"),
                    r.optionalDouble("elevation_m"), null, false));
        }
        for (Row r : read(edgesCsv, "edges")) {
            long source = r.longValue("source");
            long target = r.longValue("destination");
            if (graph.getNode(source) == null || graph.getNode(target) == null) {
                throw r.error("edge references unknown node");
            }
            graph.addEdge(new Edge(r.longValue("id"), source, target, r.doubleValue("distance_meters"),
                    r.text("road_type"), r.doubleValue("speed_limit_kmh"), (int) r.longValue("capacity")));
        }
        graph.addWalkingReverseEdges();

        if (stationsCsv != null && linksCsv != null) {
            List<Node> stations = new ArrayList<>();
            for (Row r : read(stationsCsv, "rail_stations")) {
                stations.add(new Node(STATION_ID_BASE - r.longValue("id"), r.doubleValue("latitude"),
                        r.doubleValue("longitude"), r.optionalDouble("elevation_m"), r.text("name"), true));
            }
            List<Graph.RailLink> links = new ArrayList<>();
            for (Row r : read(linksCsv, "rail_links")) {
                links.add(new Graph.RailLink(STATION_ID_BASE - r.longValue("from_station"),
                        STATION_ID_BASE - r.longValue("to_station"), r.text("line"), r.doubleValue("distance_meters")));
            }
            graph.addRailNetwork(stations, links);
        }
        return graph;
    }

    // ---- minimal CSV reading (quoted fields supported, no embedded newlines) ----

    private static List<Row> read(InputStream in, String label) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException(label + " CSV is empty");
            Map<String, Integer> header = new HashMap<>();
            List<String> names = split(headerLine.replace("﻿", ""));
            for (int i = 0; i < names.size(); i++) header.put(names.get(i).trim(), i);
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (!line.isBlank()) rows.add(new Row(label, lineNo, header, split(line)));
            }
        }
        return rows;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private record Row(String label, int lineNo, Map<String, Integer> header, List<String> cells) {
        String text(String column) {
            Integer i = header.get(column);
            if (i == null) throw error("missing column '" + column + "'");
            return i < cells.size() ? cells.get(i).trim() : "";
        }

        long longValue(String column) {
            try { return Long.parseLong(text(column)); } catch (NumberFormatException e) { throw error("bad number in " + column); }
        }

        double doubleValue(String column) {
            try { return Double.parseDouble(text(column)); } catch (NumberFormatException e) { throw error("bad number in " + column); }
        }

        double optionalDouble(String column) {
            if (!header.containsKey(column)) return Double.NaN;
            String v = text(column);
            if (v.isEmpty()) return Double.NaN;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { throw error("bad number in " + column); }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Malformed " + label + " CSV at line " + lineNo + ": " + message);
        }
    }
}
