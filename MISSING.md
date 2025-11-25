MISSING IMPLEMENTATION COVERAGE (APPROACH.md vs current code)
============================================================

The items below remain incomplete relative to the design in APPROACH.md.

1) Migration realism and network effects
---------------------------------------
- Containers are flagged paused but no explicit pause/resume of execution state is modeled; transfer contention is simplified (bandwidth divided by active transfers) with no per-link queueing or latency jitter.
- Migration scoring/selection still ignores latency and bandwidth weights; only transfer timing uses network params.
- Network effects on application execution (post-migration latency impact) are not modeled.

2) Gossip protocol fidelity
---------------------------
- Gossip immutability (only self-updates) is not enforced—merged tables accept arbitrary entries.
- Payload size/serialization overhead is approximated by entry count; JSON sizing and link cost are not modeled.

3) Configuration application gaps
---------------------------------
- Network bandwidth/latency configs are used only for migration transfer timing; they are not used in scoring or feasibility checks.
- Fault type probabilities exist but fault severity parameters (percent degradation) remain hardcoded.

4) Bidding/SLA nuance
---------------------
- Cevaluated still omits bidder reliability history and SLA penalty per bidder beyond a simple accumulated SLA penalty; PAYMENT_EVENT remains unused (payments occur inline).
- BID_REQUEST is used, but bidding ignores communication delay/timeout variability.

5) Metrics and outputs
----------------------
- Energy consumption and detailed network overhead (bytes per hop, serialization cost) are not captured.
- Migration overhead logs bandwidth only; CPU/latency overhead remains zero.
- Availability/makespan are coarse; no deadline compliance breakdown by mode or workload class.

6) Centralized vs distributed parity
------------------------------------
- Centralized scheduler uses the same residual scoring and lacks WAN-aware placement beyond transfer timing; global bandwidth contention and latency-aware feasibility are not modeled.
