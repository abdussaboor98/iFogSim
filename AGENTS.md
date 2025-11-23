# Agent Guide for Collaborative Fault Tolerance

This file briefs AI assistants on the project layout, simulation flow, and key code paths for implementing the collaborative fault tolerance framework in `collab-ft`.

Always follow the implementation notes about the approach given in `STEPS.md` and `APPROACH.md`.

## High-Level Intent
- Simulate a three-layer edge–fog–cloud system where fog nodes collaborate when faults are predicted. Migration proceeds intra-fog, then inter-fog, then cloud, with bidding and token-based settlement.
- All new code stays under `collab-ft` Maven module; reuse iFogSim for core simulation time, events, devices, and sensors.

## Module Structure (target)
- `collab-ft/pom.xml`: Maven module depending on `ifogsim`.
- `src/main/java/org/collabft/`: Java sources.
  - `SimulationMain`: entry point; loads YAML, builds topology, starts CloudSim.
  - `config/`: `SimulationConfig`, `ConfigLoader`.
  - `model/`: `FogNodeState`, `ServerState`, `ContainerProfile`, `FaultEvent`, `GossipMessage`, `BidRequest`, `BidResponse`, `PaymentReport`, `Wallet`, `MigrationLog`.
  - `core/`: `IfogBuilder` (creates devices/entities), `DecisionAgent`, `GossipAgent`, `FaultScheduler`, `WorkloadAgent`, `MetricsSink`.
  - `util/`: math helpers for scoring, staleness checks, and simple JSON logging.
- `src/main/resources/`: `collabft-config.yaml` sample.
- `src/test/java/`: unit and small integration tests for scoring, SLA payment, and short simulation smoke.

## Event Flow Overview
1. **Bootstrap**: `SimulationMain` loads config, sets seeds, builds devices, registers agents (per fog node) and workload/fault entities, then starts CloudSim.
2. **Gossip (every interval)**: `GossipAgent` updates its own `FogNodeState`, sends `GossipMessage` to next node in ring. Receivers merge immutable tables, mark stale entries (`age > 3 * interval`), and log JSON snapshots.
3. **Fault Prediction**: `FaultScheduler` samples Poisson faults, emits `EVT_FAULT_PREDICTED` at `T_pred = T_fail - lead`, `EVT_FAULT_HIT` at `T_fail`, and `EVT_RECOVERED` after 600s. Prediction event triggers migration workflow for the affected container/server.
4. **Workload**: `WorkloadAgent` issues `ContainerProfile` arrivals mapped to fog nodes; `DecisionAgent` places them respecting one-container-per-server.
5. **Migration Workflow** (in `DecisionAgent`):
   - Phase 1: Local feasibility check on healthy servers; pick max `residualScore`.
   - Phase 2: Use gossip state to compute `alpha/beta/gamma` weights (deadline-aware), derive `S_node`, filter by `>0.3`, include cloud score, select top K (default 3). If none, pick cloud.
   - Phase 3: Send `BidRequest` to selected nodes; they compute `C_res + C_risk + C_mig` and reply. Apply communication delay and timeout.
   - Phase 4: Origin adds `C_sla` (bounded [0,1], init 0.5) to each bid, selects min, executes migration (update loads, model bandwidth depending on intra/inter/cloud).
   - Phase 5: On completion or deadline miss, compute `SLA_value = 0.6*R_norm + 0.4*U_norm`, payment `P` (`+` if met, `- eta*SLA_value` if violated), update `Wallet`, and log `PaymentReport`.
6. **Metrics**: `MetricsSink` aggregates migration times, SLA violations, network overhead (gossip + bidding bytes), load variance, fault recovery time, makespan, energy estimate, and availability; flushes to `results/collab-ft/`.

## Key Data Rules
- Normalized loads: `L_cpu = cpuUsed/cpuTotal`, same for memory and bandwidth.
- Free capacity: `1 - L_*`; `residualScore = (1-L_cpu)+(1-L_mem)+(1-L_bw)`.
- Weighting for scores: derive `p_cpu/p_mem/p_bw` from container requirements; urgency increases `gamma` toward 1; redistribute remaining weight to `alpha/beta`.
- Staleness: ignore gossip entries older than three gossip intervals.
- Single active container per server; crashed servers reject placements until recovery event.

## Integration Notes
- Use simple event tags (`EVT_GOSSIP_TICK`, `EVT_FAULT_PREDICTED`, `EVT_BID_REQUEST`, etc.) to keep traces readable.
- Keep class and variable names descriptive and short (e.g., `cpuUsed`, `bwFree`, `slaValue`).
- Any changes to upstream iFogSim should be minimal and documented in comments inside the touched files plus a note in `STEPS.md` Section 11.

## Testing Expectations
- Unit tests for scoring math, staleness filtering, bid cost breakdown, and SLA payment bounds.
- Integration test running a small (e.g., 2-node) simulation for ~200s to ensure metrics files are produced and no uncaught events occur.

## Outputs
- Logs/metrics under `results/collab-ft/` (gossip, bids, payments, summary CSV/JSON).
- Console summary at simulation end showing counts of migrations per phase, SLA violation ratio, and token deltas.
