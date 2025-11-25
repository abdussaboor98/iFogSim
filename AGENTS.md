# AGENTS.md

This document defines all system agents, their responsibilities, interactions, and implementation mapping for the *Collaborative Fault Tolerance (collab-ft)* module built on top of iFogSim2. It consolidates all functionality required to execute the decentralized and centralized scheduling experiments, YAML-driven configuration, migration workflow, bidding, gossip, SLA/payment logic, and fault injection.

---

## 1. Overview

The simulation consists of multiple interacting agents representing edge devices, fog nodes, fog servers, and the cloud. Each agent participates in executing the collaborative fault‑tolerance workflow as described in **APPROACH.md**, including initial placement, gossip‑based distributed decision-making, inter‑fog migration, cloud fallback, bidding, SLA/payment settlement, and optional centralized scheduling.

All custom logic is implemented inside a new top‑level module:

```
collab-ft
```

Modifications to iFogSim2 core files are allowed when required.

---

## 2. Agent Types and Their Roles

### 2.1 CloudDevice (Agent)

**Extends:** `FogDevice`

**Responsibilities:**

* Acts as global fallback for migration.
* Hosts centralized scheduler in *Mode 2*.
* Maintains global view of fog state in centralized mode.
* Evaluates target placement for containers when cloud scheduling is enabled.
* Tracks token balances and SLA penalties for economic evaluation.

---

### 2.2 FogNodeController (Agent)

**Extends:** `FogDevice`

**Responsibilities:**

* Coordinates initial placement of incoming tasks.
* Maintains container pools at the node level.
* Runs distributed migration logic (Mode 1):

  * Gossip propagation
  * Distributed scoring and feasibility tests
  * Bidding protocol
  * Migration initiation
* Executes SLA evaluation and payment.
* Manages local state table for neighboring nodes.
* Interfaces with FaultInjector to initiate migration for failing servers.

**Interactions:**

* Sends/receives gossip messages.
* Sends bid requests, receives bid responses.
* Communicates with child FogServer devices for deployment and migration.

---

### 2.3 FogServer (Agent)

**Extends:** `FogDevice`

**Responsibilities:**

* Executes containers (AppModules).
* Tracks per‑server resource consumption.
* Evaluates feasibility for hosting new or migrated containers.
* Participates in bidding during migration (Mode 1).
* Fails or degrades when FaultInjector triggers events.
* Recovers after specified recovery interval.

**Notes:**

* The agent enforces the concurrency model (1 or more containers depending on YAML).

---

### 2.4 EdgeDevice (Agent)

**Extends:** `FogDevice` or `Sensor`+`Actuator` pair

**Responsibilities:**

* Generates tasks according to the YAML‑configured arrival distribution.
* Sends task profiles to its mapped FogNodeController.
* Does not participate in migration or decision-making.

---

### 2.5 ContainerModule (Agent)

**Extends:** `AppModule`

**Responsibilities:**

* Represents a single task encapsulated as a container.
* Stores resource profiles: MI, RAM, bandwidth, container size, deadline.
* Generates internal tuples representing execution cycles.
* Can be paused, migrated, and resumed.

---

### 2.6 GossipAgent

**Extends:** `SimEntity`

**Responsibilities:**

* Periodically triggers gossip events as per YAML interval.
* Distributes serialized state to next fog node in ring topology.
* Updates state tables and enforces staleness rules.
* Logs gossip overhead for evaluation.

---

### 2.7 FaultInjector

**Extends:** `SimEntity`

**Responsibilities:**

* Generates Poisson‑distributed failures.
* Applies CPU failure, bandwidth degradation, or full crash.
* Schedules recovery events.
* Notifies FogNodeController to start migration workflows.

---

### 2.8 BidManager (Agent)

**Custom class**

**Responsibilities:**

* Handles bid requests and responses.
* Computes Cres, Crisk, Cmig per container.
* Enforces communication delays and timeouts.
* Selects winning bid and notifies FogNodeController.

---

### 2.9 TokenManager

**Custom class**

**Responsibilities:**

* Maintains token wallet for each fog node.
* Stores and updates Csla values.
* Performs payment settlement.
* Generates logs for evaluation.

---

### 2.10 CentralCloudScheduler (Agent)

**Activated only in Mode 2**

**Responsibilities:**

* Replaces distributed decision-making.
* Directly selects migration targets using global visibility.
* Uses same feasibility equations but without staleness.
* Bidding and gossip are disabled.

**Mode switching:**

* Controlled by YAML parameter: `simulation.mode`.

---

## 3. Event Types

Custom CloudSim events to be created:

* `GOSSIP_EVENT`
* `FAULT_EVENT`
* `RECOVERY_EVENT`
* `TASK_ARRIVAL_EVENT`
* `MIGRATION_REQUEST`
* `BID_REQUEST`
* `BID_RESPONSE`
* `MIGRATION_START`
* `MIGRATION_FINISH`
* `PAYMENT_EVENT`

---

## 4. Mapping Features in APPROACH.md to iFogSim2

### Initial Placement

* Implemented in `FogNodeController.placeNewContainer()`
* Uses feasibility + residual capacity rules.

### Migration Workflow (Phases 1–5)

* Phase 1: `FogNodeController` + FogServer feasibility checks.
* Phase 2: `StateTable` scoring + gossip data.
* Phase 3: `BidManager` for economic negotiation.
* Phase 4: Selecting winning bid.
* Phase 5: `TokenManager` computes SLAvalue and payment.

### Fault Model

* FaultInjector generates failures and triggers migration.

### Gossip Protocol

* GossipAgent sends/receives state vector JSON.
* Updates FogNodeController state tables.

### SLA Computation

* TokenManager computes Rnorm, Unorm, and SLAvalue.
* Payment done upon container completion.

### YAML Usage

All structural, behavioral, and numerical configuration is loaded via a YAML reader:

```
config/
  simulation.yaml
```

A custom loader populates:

* topology
* resource capacities
* bidding coefficients
* SLA weights
* migration parameters
* fault parameters
* edge load profiles

## 5. Modifications to iFogSim

You may modify core classes as required:

* `Controller` — to support migration events and cloud scheduler mode.
* `ModulePlacementDynamic` — to support dynamic re‑mapping.
* `FogDevice` — to support multi‑server relationships.
* `AppModule` — to add container metadata.

All modifications should be documented inside the module.

---

## 6. Centralized vs Distributed Experiment Support

The system supports two simulation modes via YAML:

### Mode 1: Distributed Fog Scheduling

* Enables gossip, bidding, decentralized scoring.
* Migration target chosen locally.

### Mode 2: Centralized Cloud Scheduling

* Disables gossip and bidding.
* Cloud performs all placement and migration.

Both runs share:

* same seed
* identical workloads
* identical fault injections
* identical topology

Ensuring valid scientific comparison.

---

## 7. Summary

This AGENTS.md file defines the architecture for implementing the **collab-ft** module in iFogSim2. Each agent is tied directly to a corresponding Java class, providing a complete blueprint for implementing the decentralized fault‑tolerant migration system described in **APPROACH.md**, with YAML-driven configuration and support for centralized scheduling comparison.
