package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.model.Graph;
import com.mumbai.evacuation.model.Node;
import com.mumbai.evacuation.model.Shelter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Evacuation shelters, loaded from {@code data/shelters.json} (or the resource
 * configured as {@code graph.shelters-resource}) and snapped to the nearest
 * road-network node.
 */
@Service
public class ShelterService {

    private static final Logger log = LoggerFactory.getLogger(ShelterService.class);

    private final Map<Long, Shelter> shelters;
    private final boolean dataVerified;

    public ShelterService(GraphService graphService, ResourceLoader resourceLoader, ObjectMapper objectMapper,
                          @Value("${graph.shelters-resource:classpath:data/shelters.json}") String sheltersResource) {
        Graph graph = graphService.getGraph();
        Resource resource = resourceLoader.getResource(sheltersResource);
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            this.dataVerified = root.path("verified").asBoolean(false);
            Map<Long, Shelter> loaded = new LinkedHashMap<>();
            for (JsonNode s : root.path("shelters")) {
                double lat = s.path("lat").asDouble();
                double lon = s.path("lon").asDouble();
                Node nearest = graph.findNearestNode(lat, lon);
                double elevation = s.path("elevationM").isNumber() ? s.path("elevationM").asDouble() : Double.NaN;
                Shelter shelter = new Shelter(s.path("id").asLong(), s.path("name").asText(), lat, lon,
                        nearest != null ? nearest.getId() : -1, s.path("capacity").asInt(),
                        s.path("floodProne").asBoolean(false), elevation, s.path("kind").asText("school"));
                loaded.put(shelter.getId(), shelter);
            }
            this.shelters = Collections.unmodifiableMap(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load shelters from " + sheltersResource, e);
        }
        log.info("Loaded {} shelters from {}", shelters.size(), sheltersResource);
        if (!dataVerified) {
            log.warn("Shelter data is marked as UNVERIFIED placeholder data — replace with official BMC data before real use.");
        }
    }

    public Collection<Shelter> getAllShelters() { return shelters.values(); }

    public Optional<Shelter> getShelter(long id) { return Optional.ofNullable(shelters.get(id)); }

    public boolean isDataVerified() { return dataVerified; }
}
