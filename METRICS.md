# METRICS.md

This document defines all performance, network, resource, economic, and fault‑tolerance metrics collected by the **collab-ft** module for evaluating decentralized fog‑layer scheduling versus centralized cloud‑layer scheduling in iFogSim2. These metrics reflect practices in state‑of‑the‑art fog/edge computing literature (2018–2025) and are required to demonstrate the correctness, efficiency, and superiority of the proposed approach.

The metrics fall into eight categories:

* Migration Metrics
* Fault‑Tolerance Metrics
* Scheduling Quality Metrics
* Resource & Load Metrics
* Network & Communication Metrics
* SLA & QoS Metrics
* Economic Metrics
* Energy Metrics

These metrics are exported into JSON, CSV, and visualization logs for analysis.

---

## 1. Migration Metrics (Core to collab‑ft)

Migration is central to the evaluation of this system; therefore granularity is required.

### **1.1 Total Migrations**

* Count of all container migrations.

### **1.2 Intra‑Fog Migrations**

* Migrations that occur *within the same fog node*.

### **1.3 Inter‑Fog Migrations**

* Migrations where a container is moved *to a different fog node*.

### **1.4 Cloud Migrations**

* Migrations from fog nodes to the cloud.
* Indicates fallback behavior.

### **1.5 Migration Time**

* Total time from pause → serialization → transfer → restore.
* Includes link bandwidth effects.

### **1.6 Migration Success Rate**

* Percentage of migrations completed before deadline expiration.

### **1.7 Migration Overhead**

* CPU and bandwidth overhead consumed during migration.

### **1.8 Migration Trigger Classification**

* `fault_triggered`
* `overload_triggered`
* `score_fallback`

---

## 2. Fault‑Tolerance Metrics

These evaluate how effectively the system recovers from predicted and actual failures.

### **2.1 Fault Recovery Time**

* Time between fault prediction and stable recovery.

### **2.2 Tasks Saved vs Tasks Lost**

* Number of containers successfully migrated before server failure.
* Number of containers lost due to insufficient time or resources.

### **2.3 System Availability**

* Fraction of time fog/collab‑ft services are operational.

### **2.4 Post‑Fault Stabilization Time**

* Time to return to normal load distribution after a fault.

---

## 3. Scheduling Quality Metrics

Used to compare decentralized fog scheduling vs centralized cloud scheduling.

### **3.1 Decision Latency**

* How long it takes to select a migration target:

  * Fog (distributed)
  * Cloud (centralized)

### **3.2 Optimality of Target Node**

* Whether chosen target had the highest available capacity according to full global state.

### **3.3 Scheduling Efficiency**

* Number of migrations that required only one decision cycle vs retries.

---

## 4. Resource & Load Metrics

Standard in fog/edge literature.

### **4.1 CPU Utilization per Node and Server**

### **4.2 Memory Utilization per Node and Server**

### **4.3 Bandwidth Utilization**

* LAN, inter‑fog links, and fog‑to‑cloud WAN.

### **4.4 Load Imbalance Index**

Computed via Jain’s fairness index or standard deviation:

```
J = (Σxi)^2 / (N Σxi^2)
```

### **4.5 Server Saturation Count**

* Number of times servers reach 100% utilization in any dimension.

---

## 5. Network & Communication Metrics

Critical because the approach uses gossip and bidding.

### **5.1 Network Overhead**

* Gossip messages
* Bid requests and responses
* Migration data transfers

### **5.2 Gossip Convergence Time**

* Time until all fog nodes have a consistent view of the system.

### **5.3 Gossip Staleness Impact**

* Number of scheduling decisions made using stale entries.

### **5.4 Control Plane Messaging Overhead**

* Total number of coordination messages per migration.

### **5.5 WAN Delay Statistics**

* Delay incurred during cloud migrations.

---

## 6. SLA & QoS Metrics

These determine the reliability and responsiveness of the scheduling framework.

### **6.1 SLA Violation Ratio**

* Percentage of tasks that miss their deadlines.

### **6.2 Task Completion Latency**

End‑to‑end latency including:

* queuing
* processing
* migration
* network delay

### **6.3 SLA Penalty vs Reward Distribution**

* For economic evaluation.

### **6.4 Deadline Tightness Impact**

* Correlation between short deadlines and migration failures.

---

## 7. Economic Metrics (collab‑ft bidding model)

These metrics show cost distribution and performance of the economic layer.

### **7.1 Total Payments**

* Tokens exchanged between fog nodes.

### **7.2 Csla Evolution per Fog Node**

* Tracks reliability profile over time.

### **7.3 Profit or Utility per Node**

* revenue − cost.

### **7.4 Bidding Overhead**

* Number of messages
* Time to complete auction

### **7.5 Cost Breakdown per Migration**

* Cres
* Crisk
* Cmig

---

## 8. Energy Metrics

iFogSim2 includes an internal power model.

### **8.1 Energy Consumption per Fog Server**

### **8.2 Total System Energy Consumption**

### **8.3 Energy vs SLA Trade‑off**

* How more aggressive migrations affect energy.

---

## 9. Comparison Metrics (Distributed vs Centralized Mode)

Used to evaluate proof‑of‑concept.

### **9.1 SLA Violations: Distributed vs Centralized**

### **9.2 Migration Count: Distributed vs Centralized**

### **9.3 Network Overhead: Gossip/Bidding vs Central Control**

### **9.4 Scheduler Efficiency**

* global vs local optimization

---

## 10. Logging & Export Formats

All metrics are exported in:

```
logs/
  metrics.csv
  migrations.json
  faults.json
  gossip.json
  sla.json
```

and in summary form:

```
results/
  summary.json
  plots/
```

---

## 11. Summary

This METRICS.md file defines the full set of quantitative outputs required to evaluate the **collab-ft** system, prove the value of inter‑fog migration, and compare decentralized fog control with centralized cloud scheduling. These metrics align with the methodology described in APPROACH.md and support publication‑quality analysis.
