package com.mumbai.evacuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.model.GeoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"security.admin-token=test-token", "tomtom.api.key=", "llm.api-key=",
        "ratelimit.enabled=false", "demo.enabled=false"})
@AutoConfigureMockMvc
class ApiTest {

    private static final String SION_FLOOD = """
            {"type":"flood","latitude":19.0390,"longitude":72.8619,"radiusMeters":1200,"blockRoads":true}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void clearDisasters() throws Exception {
        mvc.perform(delete("/api/disasters").header("X-Admin-Token", "test-token"));
    }

    @Test
    void disasterMutationsRequireTheOperatorToken() throws Exception {
        mvc.perform(post("/api/disasters").contentType(MediaType.APPLICATION_JSON).content(SION_FLOOD))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/disasters").header("X-Admin-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(SION_FLOOD))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/disasters").header("X-Admin-Token", "test-token").contentType(MediaType.APPLICATION_JSON).content(SION_FLOOD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FLOOD"));
        mvc.perform(get("/api/disasters")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidDisastersAreRejectedWith400() throws Exception {
        String[] bad = {
                "{\"type\":\"VOLCANO\",\"latitude\":19.0,\"longitude\":72.8,\"radiusMeters\":500}",
                "{\"type\":\"FIRE\",\"latitude\":19.0,\"longitude\":72.8,\"radiusMeters\":50000}",
                "{\"type\":\"FIRE\",\"latitude\":40.7,\"longitude\":-74.0,\"radiusMeters\":500}",
                "{\"latitude\":19.0,\"longitude\":72.8,\"radiusMeters\":500}"
        };
        for (String body : bad) {
            mvc.perform(post("/api/disasters").header("X-Admin-Token", "test-token")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deletingAnUnknownDisasterIs404() throws Exception {
        mvc.perform(delete("/api/disasters/nope").header("X-Admin-Token", "test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void liveRouteAvoidsActiveHazardZones() throws Exception {
        mvc.perform(post("/api/disasters").header("X-Admin-Token", "test-token")
                .contentType(MediaType.APPLICATION_JSON).content(SION_FLOOD)).andExpect(status().isCreated());

        // Dadar -> Kurla would normally pass through Sion.
        String body = "{\"fromLat\":19.0178,\"fromLon\":72.8478,\"toLat\":19.0650,\"toLon\":72.8790}";
        String json = mvc.perform(post("/api/live-route").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode route = objectMapper.readTree(json);
        assertTrue(route.path("pathFound").asBoolean());
        assertEquals("ROAD_GRAPH", route.path("routeSource").asText());
        for (JsonNode p : route.path("routeCoordinates")) {
            double d = GeoUtils.haversineMeters(19.0390, 72.8619, p.get(0).asDouble(), p.get(1).asDouble());
            assertTrue(d > 1200, "route point inside the flood zone: " + p);
        }
    }

    @Test
    void routeFromInsideAZoneLeadsOut() throws Exception {
        mvc.perform(post("/api/disasters").header("X-Admin-Token", "test-token")
                .contentType(MediaType.APPLICATION_JSON).content(SION_FLOOD)).andExpect(status().isCreated());
        String body = "{\"fromLat\":19.0390,\"fromLon\":72.8619,\"toLat\":19.0269,\"toLon\":72.8381}";
        mvc.perform(post("/api/live-route").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathFound").value(true))
                .andExpect(jsonPath("$.liveStatus").value("HAZARD_EGRESS"));
    }

    @Test
    void liveRouteValidatesCoordinates() throws Exception {
        mvc.perform(post("/api/live-route").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromLat\":0,\"fromLon\":0,\"toLat\":19.0,\"toLon\":72.8}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsEmptyMessagesAndAnswersOfflineWithoutAKey() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"where is the nearest shelter\",\"userLat\":19.06,\"userLon\":72.87}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("km away")));
    }

    @Test
    void shelterCapacityUpdatesAreValidated() throws Exception {
        mvc.perform(post("/api/shelters/1/capacity").header("X-Admin-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalCapacity\":-5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/shelters/999/capacity").header("X-Admin-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalCapacity\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void compareEndpointRunsAPreset() throws Exception {
        mvc.perform(post("/api/evacuation/compare").contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":\"sion_flood\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario.id").value("sion_flood"))
                .andExpect(jsonPath("$.naive.strategy").value("NAIVE_NEAREST"))
                .andExpect(jsonPath("$.capacityAware.strategy").value("CAPACITY_AWARE"));
        mvc.perform(post("/api/evacuation/compare").contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":\"nope\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void configAndHealthAreExposed() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorTokenRequired").value(true))
                .andExpect(jsonPath("$.liveTrafficEnabled").value(false));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
