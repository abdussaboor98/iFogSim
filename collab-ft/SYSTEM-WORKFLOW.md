# Collaborative Fault Tolerance Workflow

This document explains, in detail, how the collaborative fault-tolerance system operates on top of the edge-fog-cloud stack. It focuses on behaviors, data flows, scheduling logic, and metrics without referencing code-level constructs. Mode 1 (distributed fog scheduling) is the proposed approach; Mode 2 (centralized cloud scheduling) is the baseline.

---

## 1) Topology and Inputs
- **Layers:** Edge devices generate tasks, fog nodes orchestrate execution across their local servers, and the cloud provides global fallback and (optionally) centralized scheduling.
- **Structure:** Each fog node manages a small cluster of heterogeneous servers; one node may be over-provisioned to model skewed capacity. Edge devices are pinned to the nearest fog node for all task submissions.
- **Configuration source:** A YAML file supplies topology, hardware capacities, workload arrival distributions, migration coefficients, bidding weights, SLA/payment parameters, gossip cadence, and fault rates. Random seeds are kept consistent across modes to enable paired comparisons.

---

## 2) Roles and Responsibilities
- **Cloud layer component:** Acts as ultimate fallback for migrations, maintains global accounting of SLA penalties/rewards, and hosts the centralized scheduler in Mode 2. It keeps a holistic view of fog state only when centralized scheduling is enabled.
- **Fog node coordinator:** Performs initial placement, maintains a server pool, executes the full distributed migration workflow (gossip, scoring, bidding, initiation), evaluates SLA outcomes, and triggers payments. It also holds a neighborhood state table built from gossip updates and interfaces with the failure injector to react to predicted faults.
- **Server worker:** Hosts containers, tracks per-server resource consumption, validates feasibility for incoming or migrating containers, and participates in bidding when asked. It may be taken offline or degraded by injected faults and later recovers.
- **Edge device:** Generates tasks based on configured arrival distributions and sends task profiles to its mapped fog node. It does not participate in migration decisions.
- **Containerized task:** Encapsulates a single job with CPU/RAM/bandwidth needs, size, and deadline. It can be paused, migrated, and resumed.
- **Gossip driver:** Periodically emits serialized state to the next fog node in a logical ring, enforces staleness rules, and logs overhead.
- **Fault injector:** Produces Poisson-distributed failures (CPU loss, bandwidth degradation, or crash), schedules recovery, and notifies fog coordinators to start migration.
- **Bidding component:** Manages bid requests/responses, computes resource/risk/migration cost terms, respects communication delays/timeouts, and picks winners for migration.
- **Token wallet:** Tracks balances and SLA-derived reputation per fog node, settles payments, and logs economics.
- **Centralized scheduler:** Activated only in Mode 2 to replace distributed scoring/bidding with a global placement decision.

---

## 3) Event Model
Custom simulation events drive the workflow: gossip ticks, fault and recovery signals, task arrivals, migration requests, bid exchanges, migration start/finish, and payment triggers. These events keep the cloud, fog nodes, and servers synchronized while maintaining the ordering guarantees required for migrations.

---

## 4) End-to-End Lifecycle
1. **Task arrival and profiling:** An edge device submits a task profile (CPU, RAM, bandwidth, container size, deadline) to its fog node.
2. **Initial placement:** The fog node selects a server that passes feasibility checks (no resource dimension exceeds capacity after placement) and maximizes residual capacity to preserve headroom.
3. **Execution:** The container runs alongside others on the chosen server. Resource usage updates feed into gossip and future decisions.
4. **State dissemination (gossip):** At fixed intervals, each fog node sends its normalized CPU, memory, and bandwidth load to the next node in the ring. Normalized load is computed as current usage divided by capacity per resource dimension. Entries carry timestamps; any entry older than a defined number of intervals is treated as stale and ignored during scoring.
5. **Fault prediction/trigger:** When the fault injector announces a pending failure with a fixed lead time, every container on the at-risk server is queued for migration. Each is handled independently.
6. **Migration phases (Mode 1):**
   - **Phase A - Local recovery:** Scan healthy servers within the same fog node. A candidate is valid only if post-placement utilization in CPU, memory, and bandwidth stays at or below full capacity. Among feasible choices, pick the one with the highest remaining combined headroom, computed as the sum of residual CPU, memory, and bandwidth percentages after placement.
   - **Phase B - Neighbor pre-selection:** If local recovery fails, convert cached gossip loads into free capacity scores for each neighbor and the cloud. Free capacity per resource is calculated as 1 - normalized load. Weights for CPU/memory/bandwidth derive from the container's profile and deadline urgency (bandwidth weight rises as deadlines tighten). Only nodes above a free-capacity threshold are retained; the top-ranked set (up to a fixed fan-out) becomes bid recipients.
   - **Phase C - Bidding:** Send bid requests to selected neighbors. Each recipient performs its own server-level feasibility check and computes a bid price composed of resource cost, risk factor, and migration cost (which depends on container size and link bandwidth). Resource cost scales with projected utilization increase, risk cost scales with local failure probability, and migration cost scales with transfer time and pause time. Responses respect network delays and timeouts.
   - **Phase D - Winner selection:** The origin chooses the lowest-cost feasible bid that can complete before the deadline. Deadline feasibility is checked by comparing predicted migration completion time against remaining time to failure and task deadline. If no bids qualify, the cloud is chosen if it is still viable; otherwise, the migration fails and the container may be lost when the fault materializes.
   - **Phase E - Migration execution:** The container is paused, serialized, transferred, and restored on the target. Transfer time is estimated as container size divided by available link bandwidth plus protocol overhead. Completion triggers accounting and payment.

### Detailed phase breakdown (Mode 1)
1) **Preparation**
- Input: task resource profile, deadline, container size, predicted fault time, current time, gossip table.
- Compute time_to_deadline = deadline - now, time_to_fault = predicted_fault - now.
- Reject migration if time_to_fault <= safety buffer (not enough time); otherwise proceed.

2) **Phase A - Local recovery**
- For each healthy server in the same node:
  - Compute projected utilization: cpu_proj = (cpu_used + cpu_req) / cpu_cap, mem_proj = (mem_used + mem_req) / mem_cap, bw_proj = (bw_used + bw_req) / bw_cap.
  - Feasible if all projected utilizations <= 1.0.
  - Residual headroom = (1 - cpu_proj) + (1 - mem_proj) + (1 - bw_proj).
- Select the feasible server with maximum residual headroom. If tie, pick lowest current latency path or highest cpu headroom.
- If selected, estimate migration time (pause + transfer + resume). If migration_time < min(time_to_fault, time_to_deadline), perform migration locally and stop. Otherwise, treat as infeasible and continue.

3) **Phase B - Neighbor pre-selection**
- For each neighbor and the cloud:
  - Extract normalized loads from gossip; discard entries older than staleness window.
  - Compute free capacities: cpu_free = 1 - cpu_load, mem_free = 1 - mem_load, bw_free = 1 - bw_load.
  - Derive weights from container proportions and urgency (see formulas below): cpu_w, mem_w, bw_w.
  - Suitability score = cpu_w * cpu_free + mem_w * mem_free + bw_w * bw_free.
- Discard nodes with suitability below threshold. Rank remaining by score; keep top K (fan-out). Always include cloud if above threshold or if no neighbor passes threshold.
- If no candidate remains, proceed to cloud-only fallback scoring; if cloud also fails threshold, mark migration as likely to fail.

4) **Phase C - Bidding**
- Multicast bid requests to selected candidates; each request includes resource demands, container size, deadline, and time_to_fault.
- Each candidate:
  - Repeats server-level feasibility per server using projected utilization as in Phase A.
  - If no feasible server, returns rejection.
  - For best local server, estimate migration_time_local = pause + transfer + resume using link bandwidth between origin and candidate.
  - Compute cost terms:
    - resource_cost = k_res * (delta_cpu + delta_mem + delta_bw), where deltas are projected utilization increases; optionally multiply by congestion_factor if any projected utilization exceeds soft threshold.
    - risk_cost = k_risk * local_fault_prob * (1 + staleness_flag).
    - migration_cost = k_mig * (container_size / effective_bw + pause_latency + resume_latency).
  - total_bid = resource_cost + risk_cost + migration_cost.
  - If migration_time_local exceeds min(time_to_fault, time_to_deadline), return rejection.
- Requests expire after bid_timeout; late responses are ignored.

5) **Phase D - Winner selection**
- Collect bids received before timeout and marked feasible.
- Filter by deadline: migration_time_estimate must be < min(time_to_fault, time_to_deadline).
- Choose bid with lowest total_bid. Tie-break by higher residual headroom at destination, then by lower network latency.
- If no bids remain, evaluate cloud fallback with same feasibility and timing checks using WAN bandwidth; if cloud fails, mark migration as failed.

6) **Phase E - Migration execution**
- Execute pause, serialize, transfer, and resume:
  - pause_time = base_pause + pause_factor * container_memory.
  - transfer_time = (container_size * safety_margin) / effective_bandwidth.
  - resume_time = base_resume + resume_factor * container_memory.
- migration_time = pause_time + transfer_time + resume_time.
- If migration_time still fits within min(time_to_fault, time_to_deadline) at execution moment, proceed; otherwise abort and log failure.
- On success, update resource usage at source and destination, record migration event, trigger payment flow.

7) **Accounting and settlement**
- Compute SLA outcome: success if task completes before deadline; violation otherwise.
- Payment = agreed bid amount plus SLA reward/penalty adjustment based on outcome.
- Update token balances and reputation scores; log payer, payee, amount, reason, and timestamps.
7. **Payment and SLA accounting:** After migration or task completion, SLA compliance is evaluated. Rewards or penalties adjust each fog node's token balance and reputation metric; all transactions are logged for later analysis.
8. **Recovery:** Once the failed server recovers, it re-enters the resource pool and resumes normal feasibility checks for future placements.

---

## 5) Centralized Baseline (Mode 2)
- **Control flow:** Gossip and bidding are disabled. Fault notifications and migration requests are forwarded to the cloud-side scheduler, which has a global view of node loads and selects targets directly.
- **Selection logic:** The same feasibility and scoring equations are applied, but with up-to-date global state instead of potentially stale gossip data. Network and serialization costs are still considered, and WAN penalties make the cloud less attractive unless strictly necessary.
- **Fallbacks:** If the cloud determines no fog node can host the container within deadline constraints, the container is offloaded to the cloud itself; otherwise, it is dropped when the fault occurs.
- **Comparability:** Both modes share the same seeds, workloads, and injected faults so that differences stem only from distributed versus centralized decision-making.

---

## 6) Data, Logs, and Artifacts
- **Configuration:** `collabft-config.yaml` provides all runtime knobs for experiments (topology, resource caps, deadlines, fault rates, gossip intervals, bidding weights, SLA coefficients).
- **Logs:** Metrics and traces are emitted to CSV/JSON in the logs directory (network traces, migration details) and summarized in the results directory for visualization.
- **State tables:** Gossip-derived neighborhood views are maintained locally and refreshed each interval; stale entries are excluded from scoring.

---

## 7) Metrics Reported
- **Migration:** Counts (total, intra-fog, inter-fog, fog-to-cloud), migration time, success rate, overhead, and trigger classification.
- **Fault tolerance:** Recovery time, tasks saved vs lost, availability, and post-fault stabilization time.
- **Scheduling quality:** Decision latency, whether the chosen target matches the best available option given full state, and number of migrations completed in a single decision cycle.
- **Resource and load:** CPU, memory, and bandwidth utilization per node and server; load imbalance index; server saturation counts.
- **Network and communication:** Control-plane overhead for gossip and bidding, migration data volume, convergence time, staleness impact, WAN delay statistics.
- **SLA and QoS:** Deadline violation ratio, end-to-end task latency (queuing, processing, migration, network), SLA penalty/reward distribution, and deadline tightness effects.
- **Economic:** Total payments, evolution of reliability-driven reputation scores, profit/utility per node, bidding overhead, and cost breakdown per migration (resource, risk, migration cost terms).
- **Energy:** Per-server and total system energy consumption plus trade-offs between aggressive migration and energy use.
- **Mode comparison:** Differences between Mode 1 and Mode 2 in SLA violations, migration counts, network overhead, and scheduler efficiency.

### Calculation details
- **Normalized loads:** For each server, divide consumed CPU, memory, and bandwidth by respective capacities to obtain values in [0,1]. Gossip shares these normalized values.
- **Residual capacity score (local and initial placement):** After hypothetically adding the container, compute residuals as 1 - projected utilization for CPU, memory, and bandwidth. Sum the three residuals; the highest sum wins.
- **Neighbor score:** Convert gossip loads to free capacities (1 - load), then combine as weighted sum using dynamically derived weights based on container proportions and urgency. Bandwidth weight is increased toward 1 as the deadline tightens; remaining weight is split proportionally between CPU and memory. The weighted sum yields a suitability value in [0,1].
- **Selection threshold:** Neighbors with combined weighted free capacity below the configured threshold are discarded. If more than the multicast fan-out qualify, keep the top-scoring subset; otherwise, invite all qualifiers plus cloud fallback.
- **Migration time estimate:** Pause time + (container size / effective bandwidth) + resume time. Effective bandwidth accounts for link type (LAN, inter-fog, WAN) and any degradation injected by faults. Serialization overhead is added as a fixed fraction of container size; deserialization overhead is added as a fixed latency.
- **Bid pricing:** Total bid = resource cost (proportional to added utilization and remaining headroom) + risk cost (proportional to local failure likelihood or staleness) + migration cost (proportional to estimated migration time and data volume). Resource cost may include a nonlinear penalty as utilization approaches saturation; migration cost scales with network path (LAN < inter-fog < WAN). Timeouts discard late bids.
- **Deadline feasibility:** Migration is allowed only if predicted completion precedes both the fault arrival (for the source server) and the task deadline (for the workload).
- **SLA accounting:** Deadline violations increment violation counts and apply penalties; on-time completion yields rewards. Net token change per node is reward minus penalty across all tasks.
- **Availability:** Ratio of time the system can accept or execute tasks versus total simulated time, with downtime windows derived from injected faults.
- **Load imbalance:** Calculated via fairness index or standard deviation across server utilizations using normalized loads per server at sampling points.
- **Staleness handling:** Gossip entries older than the staleness window are excluded from neighbor scoring; any decision using such entries increments staleness-impact counters.
- **Energy:** Derived from built-in power models using utilization traces; aggregated per server and across the system.

### Scoring and weighting formulas
- **Container proportion weights:** Let cpu_req, mem_req, bw_req be the task's demands; let total = cpu_req + mem_req + bw_req. Base proportions are cpu_p = cpu_req/total, mem_p = mem_req/total, bw_p = bw_req/total.
- **Urgency factor:** urgency_raw = 1 / (time_to_deadline + epsilon). urgency_norm = min(1, urgency_raw * scale), where scale tunes sensitivity.
- **Bandwidth emphasis:** bw_weight = bw_p + urgency_norm * (1 - bw_p). This pushes bandwidth weight toward 1 as deadlines shrink.
- **CPU/memory redistribution:** remaining = 1 - bw_weight. cpu_weight = remaining * cpu_p / (cpu_p + mem_p). mem_weight = remaining * mem_p / (cpu_p + mem_p).
- **Neighbor suitability:** suitability = cpu_weight * cpu_free + mem_weight * mem_free + bw_weight * bw_free, where free terms are 1 - normalized_load from gossip.
- **Cloud suitability:** same weighted formula but with fixed free values (1,1,cloud_bw_factor) to reflect WAN limits; cloud_bw_factor < 1 to model higher latency/lower throughput.

### Cost breakdown and winner selection
- **Bid value:** bid = migration_time + k_resource_impact * (delta_cpu + delta_mem + delta_bw).
- **Migration time:** pause + container_size / claimed_bw + resume, where claimed_bw is self-reported by bidders.
- **Trust-aware selection:** If trust is enabled, effective_bid = bid / trust. Nodes with trust below the threshold are skipped; dishonest claims decay trust, accurate delivery recovers it.
- **Tie-breaking:** Lowest effective bid wins; ties prefer more headroom after placement, then lower latency path. Gossip is used only to shortlist candidates.

### Migration scheduling details
- **Pause/Resume:** Fixed durations from configuration; applied to migration time and actual timing checks.
- **Transfer:** transfer_time = container_size / claimed_bw. Feasibility requires projected_cpu/mem/bw <= 1 and migration_time < min(time_to_fault, time_to_deadline).
- **Overall window check:** migration_time must fit within both deadline slack and any known fault horizon.

### SLA and payment calculations
- **SLA metric:** Monitors deadline adherence (met vs violated) without influencing bidding.
- **Payments:** If the migrated task finishes before its deadline, payment = bid_value; otherwise payment = 0. Sender pays receiver; payments are logged with claimed vs actual bandwidth/time and trust adjustments.

### Additional metric calculations
- **Decision latency:** Timestamp at migration trigger to timestamp at target selection. Logged separately for distributed and centralized modes.
- **Network overhead:** Sum of bytes for gossip messages, bid requests/responses, and migration data payloads. Gossip overhead = message_size * gossip_rounds; bid overhead = request_size + response_size per participant.
- **Migration overhead (CPU/bandwidth):** CPU overhead estimated as serialization_cpu_factor * container_memory; bandwidth overhead equals container_size plus protocol padding divided by link duration.
- **Gossip convergence:** Number of intervals until all nodes have received at least one fresh state from every other node; tracked via last-update timestamps.
- **Staleness impact counter:** Incremented when a decision excludes entries older than the staleness limit or when no neighbor qualifies due to stale data.
- **Fault recovery time:** From predicted fault notification to completion of all migrations tied to that fault event.
- **Tasks saved vs lost:** Saved = containers migrated before failure; lost = containers still on failed server at failure time or migrations that miss deadlines.
- **Load imbalance index:** Fairness = (sum u_i)^2 / (n * sum u_i^2) using per-server utilization u_i. Lower values imply higher imbalance.
- **Server saturation count:** Incremented when any resource dimension reaches or exceeds capacity during sampling.
- **Energy accounting:** Instantaneous power derived from utilization via the configured power model; energy = integral of power over time per server, summed for system totals.

---

## 8) Key Timing Assumptions
- **Prediction lead time:** Fixed window between predicted fault notification and actual failure, used to bound migration time.
- **Gossip cadence:** Fixed interval circulation around the ring; entries older than a set multiple of this interval are marked stale.
- **Timeouts:** Bid responses must arrive within configured delays; late responses are discarded to prevent blocking migrations.

---

## 9) Evaluation Goals
- Demonstrate that distributed fog-level cooperation (Mode 1) preserves more tasks before failures, with lower decision latency and balanced resource use, while incurring controlled communication overhead via gossip and bidding.
- Use Mode 2 to quantify the cost of removing distributed cooperation: higher WAN usage, potentially slower reaction due to centralized arbitration, but decisions based on authoritative global state.

---

This workflow summary provides a complete, code-agnostic view of how tasks flow, how migrations are decided and executed, how faults are handled, and how outcomes are measured under both distributed and centralized scheduling regimes.
