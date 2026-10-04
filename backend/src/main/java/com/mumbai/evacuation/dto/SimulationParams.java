package com.mumbai.evacuation.dto;

/**
 * Tunable simulation assumptions.
 *
 * @param personsPerVehicle     people per car/bus for drivers (road load = drivers / this / window)
 * @param evacuationWindowHours how long the evacuation is spread over
 * @param capacityScale         multiplier on every shelter's remaining capacity (sensitivity analysis)
 * @param walkShare             share of each group that walks (0–1)
 * @param transitShare          share that walks to a station and takes the train (0–1); the rest drive
 */
public record SimulationParams(double personsPerVehicle, double evacuationWindowHours, double capacityScale,
                               double walkShare, double transitShare) {

    public SimulationParams {
        if (personsPerVehicle <= 0) throw new IllegalArgumentException("personsPerVehicle must be positive");
        if (evacuationWindowHours <= 0) throw new IllegalArgumentException("evacuationWindowHours must be positive");
        if (capacityScale <= 0) throw new IllegalArgumentException("capacityScale must be positive");
        if (walkShare < 0 || transitShare < 0 || walkShare + transitShare > 1.0 + 1e-9) {
            throw new IllegalArgumentException("walkShare and transitShare must be >= 0 and sum to at most 1");
        }
    }

    public double driveShare() {
        return Math.max(0, 1.0 - walkShare - transitShare);
    }

    public SimulationParams withPersonsPerVehicle(double v) { return new SimulationParams(v, evacuationWindowHours, capacityScale, walkShare, transitShare); }
    public SimulationParams withWindowHours(double v) { return new SimulationParams(personsPerVehicle, v, capacityScale, walkShare, transitShare); }
    public SimulationParams withCapacityScale(double v) { return new SimulationParams(personsPerVehicle, evacuationWindowHours, v, walkShare, transitShare); }

    /** Sets the walking share; drivers absorb the difference (transit unchanged, clamped to fit). */
    public SimulationParams withWalkShare(double v) {
        return new SimulationParams(personsPerVehicle, evacuationWindowHours, capacityScale, v, Math.min(transitShare, 1.0 - v));
    }

    public SimulationParams withTransitShare(double v) {
        return new SimulationParams(personsPerVehicle, evacuationWindowHours, capacityScale, Math.min(walkShare, 1.0 - v), v);
    }
}
