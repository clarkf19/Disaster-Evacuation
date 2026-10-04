package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.disaster.DisasterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chronic monsoon water-logging spots ("monsoon risk" layer), loaded from
 * {@code data/flood_hotspots.json}. They can be shown on the map, activated as
 * live flood zones, or used as a preset simulation scenario.
 */
@Service
public class FloodHotspotService {

    private static final Logger log = LoggerFactory.getLogger(FloodHotspotService.class);

    public record Hotspot(String id, String name, double lat, double lon, double radiusMeters, Double elevationM) {}

    private final List<Hotspot> hotspots;
    private final boolean verified;

    public FloodHotspotService(ResourceLoader resourceLoader, ObjectMapper objectMapper,
                               @Value("${graph.hotspots-resource:classpath:data/flood_hotspots.json}") String hotspotsResource) {
        Resource resource = resourceLoader.getResource(hotspotsResource);
        List<Hotspot> loaded = new ArrayList<>();
        boolean isVerified = false;
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                isVerified = root.path("verified").asBoolean(false);
                int i = 1;
                for (JsonNode h : root.path("hotspots")) {
                    loaded.add(new Hotspot("hotspot-" + i++, h.path("name").asText(), h.path("lat").asDouble(),
                            h.path("lon").asDouble(), h.path("radiusMeters").asDouble(350),
                            h.path("elevationM").isNumber() ? h.path("elevationM").asDouble() : null));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not load flood hotspots from " + hotspotsResource, e);
            }
        }
        this.hotspots = List.copyOf(loaded);
        this.verified = isVerified;
        log.info("Loaded {} monsoon flood hotspots", hotspots.size());
    }

    public List<Hotspot> getHotspots() { return hotspots; }
    public boolean isVerified() { return verified; }

    /** Every hotspot as a road-blocking flood zone. */
    public List<DisasterEvent> asFloodEvents(String idPrefix) {
        return hotspots.stream()
                .map(h -> new DisasterEvent(idPrefix + h.id(), DisasterType.FLOOD, h.lat(), h.lon(), h.radiusMeters(),
                        true, 3.0, "Monsoon water-logging: " + h.name()))
                .toList();
    }
}
