package com.mumbai.evacuation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Node-to-node route query on the road graph. {@code algorithm} is ASTAR (default) or DIJKSTRA. */
public record RouteRequest(
        @NotNull Long sourceNodeId,
        @NotNull Long targetNodeId,
        @Pattern(regexp = "(?i)ASTAR|DIJKSTRA", message = "must be ASTAR or DIJKSTRA") String algorithm) {
}
