package com.mumbai.evacuation.model;

/**
 * NAIVE_NEAREST   — every group goes to its closest reachable shelter, ignoring
 *                   capacity and the traffic other groups create (baseline).
 * CAPACITY_AWARE  — greedy assignment that respects remaining shelter capacity,
 *                   splits groups across shelters when needed, accounts for the
 *                   traffic earlier groups put on the roads, and re-routes groups
 *                   whose route got >= 20% slower once all traffic is loaded.
 */
public enum EvacuationStrategy {
    NAIVE_NEAREST,
    CAPACITY_AWARE
}
