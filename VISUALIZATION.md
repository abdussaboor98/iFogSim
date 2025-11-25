# VISUALIZATION.md

This document describes all visualization options, output formats, logging strategies, and recommended tooling for graphically inspecting and debugging the *Collaborative Fault Tolerance (collab-ft)* simulation built on top of iFogSim2. Since iFogSim2 does not include a built-in GUI, visualization is achieved through a combination of exported logs, graph exports, event traces, and external visualization tools.

The goal is to provide:

* A visual view of the topology
* A visual view of migrations and faults
* A visual view of load balancing, SLA results, and bidding
* A visual comparison between decentralized and centralized scheduling modes

This file defines exactly how to generate these views.

---

## 1. Overview of Visualization Approach

The **collab-ft** module supports four visualization paths:

1. **Topology Graph Visualization** – export topology as GraphML or JSON and view in Gephi, yEd, or Graphviz.
2. **Event Timeline Visualization** – export migration, fault, gossip, and bidding events as CSV/JSON and visualize using Python or Grafana.
3. **Resource & SLA Time Series Visualization** – automatically log resource loads, SLA violations, and migration times and plot them.
4. **Optional Web-Based Live Visualization** – use a WebSocket/D3.js-based dashboard for real-time animation of migrations.

All visualizations are decoupled from the internal simulation engine and triggered through logging hooks inside the **collab-ft** module.

---

## 2. Topology Visualization

### 2.1 Exporting the Topology

The simulation topology—cloud → fog nodes → fog servers → edge devices—can be exported at startup using a utility method inside `collab-ft`:

```
TopologyExporter.exportGraphML("topology.graphml", fogDevices);
```

or

```
TopologyExporter.exportJson("topology.json", fogDevices);
```

### 2.2 Viewing the Graph

Use any of the following tools:

* **Gephi** (recommended) – best for interactive exploration
* **yEd** – clean diagrams
* **Graphviz** – static layouts (dot/neato)

### 2.3 What the Graph Shows

* Hierarchical structure (Cloud → Fog Nodes → Fog Servers → Edges)
* Bandwidth-capacity edges
* Latency annotations
* Node types color-coded
* Server heterogeneity

This is ideal for presenting system structure in the evaluation section.

---

## 3. Migration Event Visualization

### 3.1 Event Export

Whenever a migration occurs, the `FogNodeController` logs:

```
{
  "time": 123.4,
  "containerId": "C-17",
  "from": "FogNode3_Server2",
  "to": "FogNode1_Server1",
  "reason": "fault|overload|score-fallback",
  "migrationTime": 0.87,
  "sizeMB": 120
}
```

Events are stored in:

```
logs/migrations.json
```

### 3.2 Visualizing Migration Paths

Use **D3.js force-directed graph** or Gephi.

Recommended structure for D3:

* nodes = fog servers + cloud
* links = network edges
* animated circles = migrating containers
* color = container type / SLA level
* stroke = fault-triggered or overload-triggered

This clearly illustrates:

* intra-fog migrations
* inter-fog migrations
* cloud fallback
* load balancing effectiveness

---

## 4. Fault Timeline Visualization

### 4.1 Fault Event Logging

`FaultInjector` logs events as:

```
{
  "time": 300.0,
  "server": "FogNode2_Server1",
  "faultType": "cpu_failure|server_crash|bandwidth_degradation",
  "recovery": 600.0
}
```

Stored in:

```
logs/faults.json
```

### 4.2 Plotting Fault Impact

Using Python:

* Plot faults on a time axis
* Overlap migrations
* Show SLA violations during/post-fault

This helps explain how your system handles disruptions.

---

## 5. Gossip & State Table Visualization

### 5.1 Logging Gossip Messages

Each gossip broadcast is logged:

```
{
  "from": "FogNode3",
  "to": "FogNode4",
  "time": 240.0,
  "cpuLoad": 0.71,
  "memLoad": 0.65,
  "bwLoad": 0.40,
  "stale": false
}
```

Location:

```
logs/gossip.json
```

### 5.2 Visualizing System Load Over Time

Use Grafana or Python to show:

* CPU load
* Memory load
* Bandwidth load
* Staleness indicators

You can generate heatmaps:

* X-axis = fog nodes
* Y-axis = time
* Color = load

Ideal for demonstrating distributed state consistency.

---

## 6. SLA & Payment Visualization

### 6.1 SLA Event Logging

Each task completion logs:

```
{
  "containerId": "C-55",
  "slaMet": true,
  "slaValue": 0.73,
  "rewardOrPenalty": +0.73,
  "finalPayment": 3.14
}
```

### 6.2 Plotting SLA Trends

Use:

* histogram of SLA violations
* time series of rewards/penalties
* comparison plots between distributed vs centralized mode

---

## 7. Comparison Between Distributed vs Centralized Scheduler

### 7.1 Combined Visualization

Create a *side-by-side dashboard*:

**Distributed (collab-ft):**

* Gossip rate
* Number of bids per migration
* Inter-fog migrations
* Cloud fallback count
* SLA adherence

**Centralized (cloud scheduler):**

* No gossip or bidding
* Migration decisions made instantly
* More cloud migrations if cloud latency is lower
* SLA adherence under global state awareness

### 7.2 Visualizing the Differences

Use charts:

* Bar charts comparing migration count types
* Line charts showing SLA violation over time
* Box plots for migration delays
* Network overhead per mode

---

## 8. Optional Real-Time Web Dashboard (Advanced)

### Features:

* Dynamic topology visualization
* Real-time migration animations
* Traffic flow visualization
* Fault highlighting (server turns red)
* SLA counters

### Technology:

* WebSocket server in collab-ft
* Frontend using D3.js
* JSON event stream for updates

This provides an interactive, animated view similar to a real system.

---

## 9. File Formats & Directory Structure

```
logs/
  migrations.json
  faults.json
  gossip.json
  sla.json
  resources.csv
  network.csv
  load.csv

exports/
  topology.graphml
  topology.json
```

These files feed into visualization pipelines.

---

## 10. Tools Recommended

### Graph Visualization

* **Gephi**
* **Graphviz (dot)**
* **yEd**

### Time-Series & Metrics

* **Grafana + Prometheus**
* **Python (matplotlib / seaborn)**
* **Excel / Google Sheets**

### Real-Time

* **D3.js**
* **WebSockets**

---

## 11. Summary

This visualization pipeline provides complete graphical exploration of:

* topology
* migrations
* faults
* gossip
* SLA
* load evolution
* distributed vs centralized mode comparison

It enables clear presentation of results in your RP, thesis, or publication and allows intuitive debugging of the collab-ft system.
