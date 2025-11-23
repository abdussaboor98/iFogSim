Collaborative Fault Tolerance for Edge-Fog-Cloud Systems

Draft Proposal
November 23, 2025

1. Problem Statement

Modern Internet of Things (IoT) applications generate continuous, heterogeneous, and latency-sensitive workloads such as real-time video analytics, industrial monitoring, and smart city systems. These workloads originate from resource-constrained edge devices that lack the computational capacity to process data locally. To ensure strict Quality of Service (QoS) and meet Service Level Agreements (SLA), tasks are offloaded to more capable nodes within an edge-fog-cloud computing hierarchy.

However, this distributed and multi-tiered execution environment introduces significant reliability challenges. The interaction between numerous edge, fog, and cloud resources increases the likelihood of component failures, network degradation, and performance bottlenecks. Such issues can lead to service interruptions and inefficient resource utilization. As workloads flow across tiers, the failure of even a single node can affect dependent services, making coordinated fault-tolerant mechanisms necessary for maintaining continuous operation.

This study considers a three-layer edge-fog-cloud environment where computing resources are organized hierarchically:

The Edge Layer: Consists of lightweight, stationary, and geographically distributed devices that generate computational tasks.

The Fog Layer: Consists of multiple fog nodes, each composed of a cluster of heterogeneous servers. Every server provides a configurable combination of CPU cores, memory, storage, and network bandwidth, forming the primary execution environment for offloaded containers. Fog servers are connected through a local area network that allows low-latency intra-fog communication and migration. Each fog node continuously monitors the health and utilization of its servers. When a fault is detected or predicted by an external monitoring module, the fog node decides whether to migrate containers to other servers within the same node (intra-fog migration) or to communicate with neighboring fog nodes for inter-fog migration.

The Cloud Layer: Provides large-scale computing, global coordination, and serves as a fallback destination for tasks that cannot be maintained at the fog level.

In this work, fault prediction itself is considered out of scope. It is assumed that a pre-trained predictive model or monitoring module is available to identify potential server or network failures. The proposed framework focuses on the decision-making and communication mechanisms that determine where and how containers should be migrated or offloaded once a fault is detected or predicted. The objective is to maintain service continuity through efficient intra-fog, inter-fog, or fog-to-cloud migration while minimizing overhead, delay, and resource imbalance.

2. Objective

The main objective of this study is to design a collaborative container offloading and migration framework for edge-fog-cloud computing that ensures reliable task execution when faults are predicted or detected. The framework aims to:

Define a hierarchical three-layer system consisting of lightweight edge devices that generate computational tasks, fog nodes with multiple containerized servers that process these tasks, and a cloud layer that provides global management and storage.

Develop decision and communication mechanisms that allow each fog node to determine optimal migration targets based on resource availability, network conditions, and fault information received from predictive modules.

Enable hierarchical migration strategies where containers can be migrated within the same fog node (intra-fog), across neighboring fog nodes (inter-fog), or to the cloud when local recovery is not feasible.

Ensure service continuity across all layers while minimizing migration overhead and maintaining QoS and SLA compliance.

3. Contributions

The contributions made by this work are as follows:

The work presents a three-layer collaborative framework consisting of edge, fog, and cloud layers. The edge devices generate computational tasks, the fog layer executes them within containerized environments, and the cloud provides backup and large-scale coordination. The fog nodes can communicate and cooperate with each other when one experiences a fault or high load, ensuring that services continue running smoothly even during failures.

The framework enables flexible migration strategies where containers can move within the same fog node (intra-fog), to neighboring fog nodes (inter-fog), or to the cloud when local resources are insufficient. Migration decisions are made based on available resources, predicted faults, and current network conditions. This adaptability helps maintain continuous and reliable service operation.

The work introduces distributed decision-making at the fog layer. Each fog node independently monitors its own servers and collaborates with nearby nodes to determine the best migration targets. This removes reliance on a centralized controller and improves both responsiveness and reliability during fault recovery.

The proposed collaborative design minimizes delay and network overhead by performing most decision-making at the fog layer. Even if some servers fail, other fog nodes can quickly take over, enabling seamless task continuity and improved system resilience across the edge-fog-cloud hierarchy.

4. System Models and Configuration

To ensure reproducibility and flexibility, the system topology, fault characteristics, and operational parameters are defined externally using a YAML configuration file. This allows for dynamic adjustment of the simulation environment.

4.1 Topology & Deployment Model

Fog Nodes: The exact number of fog nodes is fully configurable via the YAML file.

Initial Configuration: 6 Fog Nodes.

Servers per Fog Node: The number of computing servers available within each fog node is configurable.

Initial Configuration: 3 Servers per Node.

Server Configuration: The specific resource capacities (CPU, RAM, Bandwidth) for the servers are configurable.

Edge Devices: The total count of edge devices and their specific mapping to fog nodes are configurable.

Cloud Configuration: The system includes a single Cloud node acting as the ultimate fallback.

Capacity Rule: The Cloud node is automatically provisioned with capacity equal to five times the capacity of the largest fog node in the system.

4.2 Fault Model

Faults are introduced into the system to rigorously test resilience. The fault generation engine adheres to the following rules:

Distribution: Fault occurrences follow a Poisson distribution.

Frequency: Faults occur at a specific rate of one every 5 minutes (wall clock time).

System-Wide Constraint: To prevent total system collapse during testing, the simultaneous number of faults across the system cannot exceed total_num_of_fog_nodes / 2.

Fault Types: When a fault is triggered, its type is selected randomly from:

CPU Failure: Drastic reduction in processing capability.

Full Server Crash: Complete unavailability of the server node.

Network Degradation: Significant reduction in available bandwidth.

Recovery Model: A failed server or component is automatically recovered and returned to the pool of available resources after 10 minutes.

4.3 Container & Workload Model

Task Arrival: Tasks arrive from edge devices at a constant rate.

Resource Requirements: Each task (container) has specific requirements ($R_{cpu}, R_{mem}, R_{bw}, S_{container}, T_{deadline}$) drawn from configurable distribution values.

Concurrency Constraint: To simplify the resource interference model, the system enforces a strict limit of 1 active container per server.

5. Methodology

The proposed methodology employs a collaborative, multi-phase decision framework initiated by fault prediction. It integrates a periodic, low-overhead state dissemination protocol with an on-demand, economic negotiation mechanism. The system is designed to handle faults generated via a random distribution model. Upon prediction of a failure, the system attempts to migrate affected containers by traversing three logical tiers: intra-fog (local), inter-fog (neighboring), and cloud (fallback).

5.1 Proactive State Dissemination (Ring-Based Gossip)

To facilitate decentralized decision-making without flooding the network, fog nodes maintain a partial view of the global system state using a gossip protocol arranged in a Ring Topology.

Topology and Propagation: Nodes are organized in a logical ring. State information is propagated periodically (every 1 minute) in a clockwise rotation.

State Table Scope: The state table maintained by each node includes entries for all fog nodes in the system, not just immediate neighbors.

Gossip Message Format: Messages are structured as POJOs (Plain Old Java Objects) and saved/logged as JSON to facilitate debugging and analysis.

Propagation Cost: A minimal cost (bandwidth and latency) is explicitly linked to the propagation of gossip messages to model real-world overhead.

Timestamping: All state entries are stamped with the current Simulation Time.

State Vector Structure: The state is maintained as a table containing critical resource metrics for each node. To ensure uniformity and ease of scoring, each server calculates these metrics as normalized load ratios in the range $[0, 1]$:

CPU Load ($L_{cpu}$): Calculated as $L_{cpu} = \frac{CPU_{used}}{CPU_{total}}$

Memory Load ($L_{mem}$): Calculated as $L_{mem} = \frac{MEM_{used}}{MEM_{total}}$

Bandwidth Load ($L_{bw}$): Calculated as $L_{bw} = \frac{BW_{used}}{BW_{total}}$

Immutability: To ensure data integrity, state elements corresponding to a specific node are immutable to others. A node may only update its own entry in the state table, while entries for other nodes are read-only during propagation.

Gossip Staleness Rule: Each state entry includes a timestamp. To prevent decisions based on obsolete data, an entry is marked as stale if its age exceeds a fixed threshold of 3 gossip intervals. Stale entries are explicitly ignored during the scoring process in Phase 2.

This mechanism ensures that every fog node possesses an eventually consistent, albeit potentially slightly stale, view of its neighbors' capacities ($S_1$), which is used solely for preliminary filtering in Phase 2.

5.2 Fault Prediction and Task Profiling

Faults are randomly generated following a specified distribution and scheduled for specific servers. To simplify the system model and focus on migration efficacy, we adopt the following assumptions regarding the fault prediction module:

Prediction Lead Time: When a fault is scheduled to occur at time $T_{fail}$, the prediction notification arrives at $T_{pred} = T_{fail} - \Delta$. We assume a configurable prediction horizon where $\Delta = 60$ seconds. This provides a fixed window for the migration workflow to execute.

Prediction Accuracy: To maintain system simplicity and deterministic evaluation, we assume the predictor has no False Positives or False Negatives. All predictions are correct, meaning every predicted fault is guaranteed to occur at $T_{fail}$.

When a fault is predicted at $T_{pred}$, the migration mechanism is triggered immediately. The system profiles the container to be migrated, encapsulating the following detailed attributes into a multicast packet:

CPU Requirement ($R_{cpu}$): The computational load required, measured in Million Instructions (MI).

RAM Requirement ($R_{mem}$): The operational memory required, measured in MB.

Bandwidth Requirement ($R_{bw}$): The minimum network throughput required for data streams, measured in Mbps.

Container Size ($S_{container}$): The total size of the container image and execution state, measured in MB (used to calculate migration transmission cost).

Deadline ($T_{deadline}$): The maximum allowable time for task completion, measured in seconds.

5.3 Migration Workflow

The migration process follows a five-phase approach, prioritizing local recovery before escalating to collaborative or cloud-based solutions.

Phase 1: Intra-Fog Migration (Local Recovery)

The Decision Engine first attempts to resolve the fault locally by scanning all operational servers within the same faulty fog node. The selection process follows a strict feasibility and optimization logic:

Feasibility Rule: A server is considered a valid candidate only if it can accommodate the container's additional load without exceeding 100% utilization in any resource dimension. A server is feasible if and only if all the following conditions are met:

$$\frac{CPU_{used}}{CPU_{total}} + \frac{R_{cpu}}{CPU_{total}} \le 1$$

$$\frac{MEM_{used}}{MEM_{total}} + \frac{R_{mem}}{MEM_{total}} \le 1$$

$$\frac{BW_{used}}{BW_{total}} + \frac{R_{bw}}{BW_{total}} \le 1$$

Selection Rule (Residual Capacity Maximization): Among the set of feasible servers, the system selects the candidate that offers the highest remaining capacity to prevent saturation. The residual score is computed as:

$$\text{residual\_score} = (1 - L_{cpu}) + (1 - L_{mem}) + (1 - L_{bw})$$

The server with the maximum residual_score is selected as the migration target.

Tie-Breaking: If two servers have an identical residual score, the system picks one at random.

Migration Time Model: Since this migration occurs within the node, the intra-fog bandwidth is modeled as LAN speed (always high-speed).

If a match is found, the container is migrated, and the workflow terminates.

Phase 2: Inter-Fog & Cloud Pre-Selection

If local resources are insufficient, the system initiates inter-fog collaboration using the cached gossip state.

Scoring: The originating node first converts the raw load metrics from the gossip state into Free Capacities:

$$CPU_{free} = 1 - L_{cpu}, \quad MEM_{free} = 1 - L_{mem}, \quad BW_{free} = 1 - L_{bw}$$

It then calculates a suitability score ($S_{node}$) for each neighboring fog node, where a higher score indicates better suitability:

$$S_{node} = \alpha \times CPU_{free} + \beta \times MEM_{free} + \gamma \times BW_{free}$$

The cloud is assigned a static score to represent its high availability but limited bandwidth (WAN constraint):

$$S_{cloud} = \alpha \times 1.0 + \beta \times 1.0 + \gamma \times 0.7$$

Here, $\alpha$, $\beta$, and $\gamma$ are tunable weights derived dynamically through the established four-step process:

Step 1: Requirement Proportions. First, normalized weights are computed based on the container's resource profile ($R_{cpu}, R_{mem}, R_{bw}$):

$$p_{cpu} = \frac{R_{cpu}}{R_{total}}, \quad p_{mem} = \frac{R_{mem}}{R_{total}}, \quad p_{bw} = \frac{R_{bw}}{R_{total}}$$

Where $R_{total} = R_{cpu} + R_{mem} + R_{bw}$.

Step 2: Deadline-Driven Urgency. To prioritize migration speed when time is critical, an urgency factor ($U_{norm}$) is calculated:

$$U = \frac{1}{T_{deadline} - T_{now} + \epsilon}$$

$$U_{norm} = \min(1, U \cdot k)$$

Here, $\epsilon$ is a small safety constant, and $k$ (typically 0.1) scales the urgency to the range $[0, 1]$.

Step 3: Bandwidth Weight Adjustment. Since migration delay is critical under tight deadlines, the bandwidth weight ($\gamma$) is increased proportionally to the urgency:

$$\gamma = p_{bw} + U_{norm} \cdot (1 - p_{bw})$$

This formula shifts $\gamma$ toward 1 as the deadline approaches.

Step 4: Weight Redistribution. The remaining weight $W = 1 - \gamma$ is distributed between CPU and Memory based on their original proportions:

$$\alpha = W \cdot \frac{p_{cpu}}{p_{cpu} + p_{mem}}, \quad \beta = W \cdot \frac{p_{mem}}{p_{cpu} + p_{mem}}$$

Ranking and Thresholding: The nodes are ranked based on their scores ($S_{node}$). The system employs an empirical selection threshold of 0.3, meaning a node must have at least 30% usable aggregated capacity to be considered.

The Cloud is always included in the ranking with its calculated score $S_{cloud}$. The selection logic for multicast targets (with $K=3$) is:

If more than $K$ nodes (including Cloud) clear the threshold ($S > 0.3$), the top $K$ are selected.

If $K$ or fewer nodes clear the threshold, all qualifying nodes are selected.

If no nodes clear the threshold, the system defaults to the Cloud immediately.

Phase 3: Bidding

Selected candidates generate and transmit bids back to the faulty node.

Communication Delay: A realistic communication delay is added for all bid requests and responses.

Response Timeout: The system enforces a configured timeout value. If bids are not received within this window, the process moves forward with available bids or defaults to cloud.

The bid is calculated as:

$$C_{bid} = C_{res} + C_{risk} + C_{mig}$$

The components are calculated using the following specific economic models:

Resource Cost ($C_{res}$): This represents the direct monetary value of the resources allocated to the container. We use simple unit constants ($unit\_cost$) to standardize the pricing:

$$C_{res} = (R_{cpu} \times 1.0) + (R_{mem} \times 0.5) + (R_{bw} \times 0.2)$$

Where 1.0, 0.5, and 0.2 represent the unit costs for CPU, RAM, and Bandwidth respectively.

Risk Cost ($C_{risk}$): This accounts for the reliability of the bidding node. A heavily loaded node is statistically more likely to fail, so it adds a "risk premium" to its bid to discourage selection unless necessary.

$$C_{risk} = p_{fail} \times 5.0$$

Where the failure probability ($p_{fail}$) is modeled simply as a function of the current CPU load ($L_{cpu}$):

$$p_{fail} = L_{cpu} \times 0.5$$

Migration Cost ($C_{mig}$): This covers the overhead of moving the container state. It is composed of the network transfer cost and the restoration cost:

Defined as:

$$C_{mig} = C_{transfer} + C_{restore}$$

$$C_{transfer} = \frac{S_{container}}{BW_{link}}, \quad C_{restore} = 0.1 \times S_{container}$$

Where $BW_{link}$ is the available bandwidth of the link between the faulty node and the bidding node.

Phase 4: Evaluation at Faulty Node

The originating node (buyer) collects the bids and calculates a final evaluation cost ($C_{evaluated}$) for each bidder. This step incorporates the historical reliability of the bidder.

$$C_{evaluated} = C_{bid} + C_{sla}$$

Where:

$C_{sla}$ is the historical penalty cost associated with the bidding node.

Normalization: $C_{sla}$ is bound between 0 and 1.

Initialization: The value is initialized to 0.5.

Decay: There is no decay mechanism for reputation; it persists until updated by a transaction.

The originating node selects the bidder with the lowest $C_{evaluated}$ and initiates the migration.

Phase 5: Settlement and Payment

The system operates on an imaginary currency called "Tokens".

Wallet: Each fog node starts with a configured amount of tokens.

Storage: Profit and loss are stored per node; every node is financially independent ("on its own").

Upon task completion, the winning node sends a final report to the originating node. The payment is processed based on the $SLA_{value}$.

SLA Value Computation: The $SLA_{value}$ is a normalized metric in the range $[0, 1]$, derived from two fundamental components:

Resource Intensity ($R_{norm}$): Measures the magnitude of resources required relative to a system-wide maximum ($R_{max}$).

$$R_{norm} = \frac{R_{cpu} + R_{mem} + R_{bw}}{R_{max}}$$

Deadline Urgency ($U_{norm}$): Measures how strict the deadline is relative to the time of the request ($T_{now}$). To prevent values from exploding as the slack approaches zero, we use a normalized bounded form:

$$U = \frac{1}{T_{deadline} - T_{now} + \epsilon}$$

$$U_{norm} = \min(1, k \cdot U)$$

Where $\epsilon$ is a small safety constant and $k$ (e.g., 0.1) is a scaling factor.

The final $SLA_{value}$ combines these components using fixed weights ($w_1 = 0.6$ for resource intensity, $w_2 = 0.4$ for urgency):

$$SLA_{value} = w_1 \cdot R_{norm} + w_2 \cdot U_{norm}$$

This ensures that the metric remains bounded ($0 \le SLA_{value} \le 1$), providing stability for the payment model.

Payment Processing: The originating node evaluates the SLA status based on the completion time versus the deadline. The payment ($P$) is calculated using a symmetric reward/penalty structure centered on the bid price:

If SLA Met ($T_{completion} \le T_{deadline}$): The node earns the bid price plus a reward proportional to the task difficulty.

$$P = C_{bid} + SLA_{value}$$

If SLA Violated ($T_{completion} > T_{deadline}$): The node is penalized, receiving the bid price minus a weighted penalty.

$$P = C_{bid} - \eta \cdot SLA_{value}$$

Where $\eta$ is a penalty severity coefficient. Since $SLA_{value} \le 1$, the penalty is bounded, preventing excessive losses for the fog node.

6. Simulation Strategy & Evaluation

6.1 Logging, Metrics, and Evaluation

The proposed framework will be evaluated using the following key metrics:

Mean Migration Time: The average time taken to successfully move a container.

SLA Violation Ratio: The percentage of tasks that missed their deadline.

Network Overhead: The bandwidth consumed by gossip propagation and bidding negotiation.

Load Imbalance: Metrics indicating the variance in utilization across nodes.

Fault Recovery Time: The time required for the system to stabilize after a fault.

Makespan Time: The total time to complete a defined batch of workloads.

Overhead: The computational cost of the decision logic.

Energy Consumption: Total energy usage derived from utilization levels.

Availability: The percentage of time services remain accessible.

6.2 Simulation Parameters & Reproducibility

To ensure the study is reproducible, the following parameters are strictly defined and configurable:

Simulation Duration: The total runtime of the experiment is configurable.

Random Seed: A fixed seed is used for fault generation and job arrival patterns to ensure consistent results across runs.