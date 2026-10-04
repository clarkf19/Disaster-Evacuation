package com.mumbai.evacuation.algorithm;

import com.mumbai.evacuation.model.Edge;

/**
 * Travel time (seconds) for traversing an edge under some scenario, or
 * {@link Double#POSITIVE_INFINITY} if the edge is impassable.
 *
 * Contract: finite costs must be >= {@link Edge#getFreeFlowSeconds()} so the
 * A* heuristic stays admissible.
 */
@FunctionalInterface
public interface EdgeCost {
    double seconds(Edge edge);

    EdgeCost FREE_FLOW = Edge::getFreeFlowSeconds;
}
