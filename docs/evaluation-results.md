# Evaluation results

Generated 2026-10-04 by `EvaluationReportTest` (`./mvnw test -Dsurefire.excludedGroups= -Dgroups=report`). Every number is reproducible from the seed.

**Method.** Each scenario was run 30 times (seed 42). In every run each group's size is scaled by a random factor in [0.7, 1.3] and its location moved up to 500 m. Both strategies run on the same randomised copy (a paired comparison). Values are the mean ± 95% confidence interval (1.96·s/√n). Default assumptions: 50% walk, 30% train, the rest drive (4 people per vehicle) over 3 h.

## Monte Carlo comparison

| Scenario | Evacuees | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Shelters over capacity — naive | Aware ≥ naive |
|---|---|---|---|---|---|---|---|
| Sion Monsoon Heavy Flood | 15,000 | 20.9 ± 1.9 | 100.0 ± 0.0 | 21.2 ± 1.7 | 43.5 ± 1.4 | 3.3 ± 0.3 | 100% |
| Bandra-Kurla Complex Major Fire | 17,000 | 11.9 ± 2.8 | 100.0 ± 0.0 | 25.4 ± 5.7 | 52.1 ± 1.9 | 3.2 ± 0.4 | 100% |
| Dadar Bridge Collapse | 18,000 | 13.5 ± 3.2 | 100.0 ± 0.0 | 10.1 ± 1.9 | 28.3 ± 1.0 | 3.9 ± 0.3 | 100% |
| Chembur Chemical Leak | 18,000 | 19.3 ± 2.5 | 100.0 ± 0.0 | 20.0 ± 2.8 | 55.7 ± 1.8 | 2.8 ± 0.2 | 100% |
| Western Suburbs Multi-Point Flood (stress test) | 82,000 | 3.9 ± 0.6 | 83.6 ± 1.1 | 26.7 ± 5.4 | 194.3 ± 2.5 | 3.7 ± 0.2 | 100% |
| Monsoon: all chronic flooding spots | 51,000 | 19.9 ± 0.9 | 92.5 ± 2.0 | 16.0 ± 1.2 | 73.6 ± 2.4 | 14.7 ± 0.6 | 100% |

## Sensitivity — western suburbs stress test

Each value: 10 randomised runs, same seeds for every value (so differences come from the parameter).

### CAPACITY_SCALE

| Value | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Congested road km — aware | Crowded rail km — aware |
|---|---|---|---|---|---|---|
| 0.5 | 2.0 ± 0.6 | 41.9 ± 0.9 | 28.4 ± 9.6 | 170.6 ± 7.7 | 22.1 ± 2.8 | 0.0 ± 0.0 |
| 1 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.4 ± 9.6 | 195.8 ± 4.9 | 30.7 ± 4.6 | 0.0 ± 0.0 |
| 1.5 | 6.1 ± 1.8 | 100.0 ± 0.0 | 28.4 ± 9.6 | 155.2 ± 7.1 | 68.5 ± 6.3 | 0.0 ± 0.0 |
| 2 | 8.1 ± 2.4 | 100.0 ± 0.0 | 28.4 ± 9.6 | 139.5 ± 7.4 | 68.7 ± 4.5 | 0.0 ± 0.0 |

### PERSONS_PER_VEHICLE

| Value | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Congested road km — aware | Crowded rail km — aware |
|---|---|---|---|---|---|---|
| 2 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.6 ± 9.5 | 198.8 ± 4.7 | 99.4 ± 11.4 | 0.0 ± 0.0 |
| 4 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.4 ± 9.6 | 195.8 ± 4.9 | 30.7 ± 4.6 | 0.0 ± 0.0 |
| 10 | 4.1 ± 1.2 | 83.7 ± 1.8 | 26.5 ± 10.0 | 192.2 ± 6.3 | 3.8 ± 1.2 | 0.0 ± 0.0 |
| 30 | 4.1 ± 1.2 | 83.7 ± 1.8 | 23.0 ± 10.8 | 191.0 ± 6.6 | 0.3 ± 0.2 | 0.0 ± 0.0 |

### WALK_SHARE

| Value | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Congested road km — aware | Crowded rail km — aware |
|---|---|---|---|---|---|---|
| 0 | 3.7 ± 1.1 | 83.7 ± 1.8 | 25.8 ± 9.1 | 103.7 ± 3.2 | 244.5 ± 10.9 | 0.0 ± 0.0 |
| 0.25 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.6 ± 9.5 | 151.2 ± 4.1 | 122.1 ± 9.8 | 0.0 ± 0.0 |
| 0.5 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.4 ± 9.6 | 195.8 ± 4.9 | 30.7 ± 4.6 | 0.0 ± 0.0 |
| 0.75 | 3.7 ± 1.1 | 83.7 ± 1.8 | 53.0 ± 4.1 | 231.3 ± 6.7 | 0.0 ± 0.0 | 0.0 ± 0.0 |

### TRANSIT_SHARE

| Value | Housed % — naive | Housed % — capacity-aware | Avg min — naive | Avg min — capacity-aware | Congested road km — aware | Crowded rail km — aware |
|---|---|---|---|---|---|---|
| 0 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.6 ± 9.5 | 173.1 ± 5.1 | 146.4 ± 11.7 | 0.0 ± 0.0 |
| 0.2 | 4.1 ± 1.2 | 83.7 ± 1.8 | 28.6 ± 9.5 | 188.6 ± 4.2 | 65.7 ± 6.4 | 0.0 ± 0.0 |
| 0.4 | 4.1 ± 1.2 | 83.7 ± 1.8 | 27.7 ± 9.7 | 204.0 ± 6.2 | 11.9 ± 2.4 | 0.0 ± 0.0 |
| 0.6 | 3.7 ± 1.1 | 83.7 ± 1.8 | 53.0 ± 4.1 | 207.2 ± 6.4 | 0.0 ± 0.0 | 0.0 ± 0.0 |


## How to read this

- **Average travel time only counts people who got a shelter place.** The naive strategy sends every group to the single nearest shelter, which is usually a small school, so it houses few people quickly and turns the rest away. Capacity-aware assignment houses far more people but sends many of them farther. Compare times only together with housed %.
- **Crowded rail km is 0 here.** At the assumed emergency capacity (30,000 people/hour per line and direction), the train share in these scenarios never saturates a line. Lower `TravelMode.RAIL_CAPACITY_PER_HOUR` to study a degraded service.
- **People per vehicle doesn't change housed %**, because it only affects road congestion (and therefore travel time).

## Caveats

- Shelter capacities are estimates and the shelter list is incomplete (see `shelters.json`). Absolute housed % depends heavily on them.
- Travel times come from a static model (speed limits, congestion steps, average train speed), not observed traffic.
- The comparison between strategies is the robust part. Absolute numbers should not be read as forecasts.
