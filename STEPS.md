# Collaborative Fault Tolerance Implementation Steps

The steps below stage the full implementation described in `APPROACH.md`. All new Java code lives under the new Maven module `collab-ft` (rooted at `collab-ft/`). We only touch upstream iFogSim classes when integration requires it; otherwise we wrap them.

## 0) Module Bootstrap
- Create `collab-ft/pom.xml` with packaging `jar`, Java 21 compatibility, and dependency on the existing `ifogsim` module via local reactor (`<module>ifogsim</module>` in root `pom.xml` if not present).
- Set standard Maven layout (`src/main/java`, `src/main/resources`, `src/test/java`).
- Add a shaded executable target `org.collabft.SimulationMain` for running the custom experiment.

## 1) Configuration Schema
- Add YAML config file `collab-ft/src/main/resources/collabft-config.yaml` capturing all tunables: topology (fog nodes, servers per node, capacities), cloud multiplier, edge-device mapping, gossip interval, staleness threshold, fault rate/poisson seed, prediction lead time, workload arrival rate, bid timeout, K multicast size, thresholds, token starting balance, SLA weights, urgency scaling, and random seeds.
- Implement `ConfigLoader` (simple Jackson YAML) returning a `SimulationConfig` POJO with nested classes for `TopologyConfig`, `FaultConfig`, `WorkloadConfig`, `GossipConfig`, `EconomicsConfig`, `SlaConfig`, and `RandomConfig`.

## 2) Core Domain Models
- Create POJOs in `org.collabft.model`: `FogNodeState`, `ServerState`, `ContainerProfile`, `FaultEvent`, `GossipMessage`, `BidRequest`, `BidResponse`, `PaymentReport`, `Wallet`.
- Keep field names simple and explicit (cpuTotal, cpuUsed, memTotal, memUsed, bwTotal, bwUsed, timestamp, etc.).
- Include helper methods for normalized loads and free capacities as described in `APPROACH.md`.

## 3) iFogSim Bindings
- Implement `IfogBuilder` to translate config into iFogSim entities (`FogDevice`, `Sensor`, `Actuator`, `Application`) while preserving the constant “one container per server” rule.
- Extend or wrap `SimEntity` for custom behaviors:
  - `GossipAgent` for periodic state dissemination.
  - `FaultScheduler` to inject predicted faults (events at `T_fail` with prediction at `T_pred = T_fail - leadTime`).
  - `DecisionAgent` to handle migration workflow and payments.
- Wire events through `CloudSim` scheduling (use `schedule` with simulation time in seconds). Keep event tags self-documenting (e.g., `EVT_GOSSIP_TICK`, `EVT_FAULT_PREDICTED`, `EVT_FAULT_HIT`, `EVT_BID_REQUEST`, `EVT_BID_RESPONSE`, `EVT_PAYMENT`).

## 4) State & Gossip (Phase 1 in Approach)
- `GossipAgent` maintains `Map<Integer, FogNodeState> stateTable`.
- Every gossip interval: update own entry, rotate in ring order (nodeId+1 mod N), attach timestamp, serialize to JSON log under `results/collab-ft/gossip/`.
- Enforce immutability by only mutating local entry; other entries are read-only copies.
- Mark entries stale if `now - entry.timestamp > 3 * gossipInterval` and exclude them during scoring.

## 5) Fault Prediction Engine
- `FaultScheduler` samples faults using Poisson(rate=1 per 300s unless overridden) respecting the cap `activeFaults <= fogCount/2`.
- For each fault: choose server uniformly, create `FaultEvent` with type (CPU drop, crash, bandwidth degradation) and recovery at `+600s`.
- Emit `EVT_FAULT_PREDICTED` at `T_pred`, `EVT_FAULT_HIT` at `T_fail`, and `EVT_RECOVERED` at recovery time to restore server capacity.

## 6) Workload Generator
- Add `WorkloadAgent` to spawn container arrivals at constant (configurable) rate with random `ContainerProfile` draws for `R_cpu`, `R_mem`, `R_bw`, `S_container`, `T_deadline`. Assign each new container to its mapped fog node’s `DecisionAgent`.
- Enforce single active container per server; if no slot, queue or drop per config.

## 7) Migration Workflow Implementation
- Phase 1 (Local): `DecisionAgent` scans healthy servers in the same fog node, checks feasibility inequalities, computes `residualScore`, picks max (random tie-break). If placed, end workflow.
- Phase 2 (Pre-Select): Convert gossip loads to free capacity, compute weights (`alpha, beta, gamma`) using requirement proportions and urgency (`T_deadline - now`). Compute `S_node`; filter by `>0.3`; include cloud score; pick top K (default 3). If none pass, default to cloud.
- Phase 3 (Bidding): Send `BidRequest` to selected nodes; add communication delay; enforce bid timeout. Each bidder computes `C_res`, `C_risk`, `C_mig` (`S_container/BW_link + 0.1*S_container`), sums to `C_bid` and returns.
- Phase 4 (Evaluation): Origin node adds `C_sla` (initialized 0.5, bounded [0,1]) to each bid, selects lowest, triggers migration to target (or cloud). Update SLA reputation per outcome.
- Phase 5 (Settlement): After completion or deadline breach, compute `SLA_value = 0.6*R_norm + 0.4*U_norm` (bounded) and payment `P` (`+` reward if met, `- eta*SLA_value` if violated). Update `Wallet` balances and log under `results/collab-ft/payments/`.

## 8) Migration Mechanics
- Model migration time: intra-fog uses LAN bandwidth; inter-fog uses configured link bandwidth between nodes; cloud uses WAN multiplier (e.g., 0.7 bandwidth factor).
- Update server load counters when container moves; block new placements on crashed servers until recovery.
- Maintain `MigrationLog` entries for metrics (source, target, phase used, durations, success flag).

## 9) Metrics & Logging
- Add `MetricsSink` writing CSV/JSON under `results/collab-ft/` for:
  - mean migration time, SLA violation ratio, network overhead (gossip + bidding bytes), load imbalance (per-interval variance), fault recovery time, makespan, energy estimate (derived from utilization), availability.
- Add deterministic seed handling from config so runs are reproducible.

## 10) Experiment Driver
- `SimulationMain` flow:
  1. Load YAML config and set seeds.
  2. Build topology and entities via `IfogBuilder`.
  3. Register agents per fog node (gossip, decision, wallet), workload generator, fault scheduler.
  4. Start `CloudSim` simulation.
  5. On finish, flush metrics and print summary to stdout.
- Provide sample config in `src/main/resources` and a ready-to-run Maven exec profile (`mvn -pl collab-ft exec:java -Dexec.mainClass=org.collabft.SimulationMain`).

## 11) Optional iFogSim Touch Points
- If needed, add minimal extensions to `ifogsim` (e.g., helper to query FogDevice resources or to mark device down). Keep changes localized and documented in a separate section within `collab-ft` README notes.

## 12) Validation Steps
- Add lightweight unit tests for scoring math, staleness, bid cost calculation, and SLA payment computation in `collab-ft/src/test/java/org/collabft`.
- Add an integration test that runs a short simulation (e.g., 200s, small topology) and asserts metrics are emitted.
- Document how to reproduce baseline and variation runs, storing outputs in `results/collab-ft/<experiment-name>/`.
