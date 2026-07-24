package com.mumbai.evacuation.model;

/**
 * Enumeration of Evacuation Routing & Shelter Assignment Strategies.
 * 
 * Design Decision Rationale:
 * - NAIVE_NEAREST (Strategy 1): Capacity-blind baseline. Every evacuee group routes to nearest shelter by distance,
 *   ignoring shelter capacity limits and accumulated traffic congestion. Used for benchmark comparison.
 * - CAPACITY_AWARE (Strategy 2): Capacity-aware greedy assignment engine. Respects shelter capacity, updates road traffic,
 *   mutates congestion factors, and triggers route recalculation if travel time increases by >= 20%.
 */
public enum EvacuationStrategy {
    NAIVE_NEAREST,
    CAPACITY_AWARE
}
