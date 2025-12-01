package org.collabft.metrics;

import org.collabft.model.ContainerModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central metrics accumulator for collab-ft experiments.
 * Captures migration/fault/scheduling/network/SLA/economic/energy metrics from METRICS.md.
 */
public class MetricsCollector {
    private final List<MigrationRecord> migrations = new ArrayList<>();
    private final List<FaultRecord> faults = new ArrayList<>();
    private final List<FaultPredictionRecord> faultPredictions = new ArrayList<>();
    private final List<PlacementRecord> placements = new ArrayList<>();
    private final List<CentralPlacementRecord> centralizedPlacements = new ArrayList<>();
    private final List<GossipRecord> gossips = new ArrayList<>();
    private final List<SlaRecord> sla = new ArrayList<>();
    private final List<LoadRecord> load = new ArrayList<>();
    private final List<ResourceRecord> resources = new ArrayList<>();
    private final List<NetworkRecord> network = new ArrayList<>();
    private final List<BidRecord> bids = new ArrayList<>();
    private final List<SettlementRecord> settlements = new ArrayList<>();
    private final List<TrustEvaluationRecord> trustEvaluations = new ArrayList<>();
    private final EconomicRecord economic = new EconomicRecord();
    private final Map<String, DecisionLatency> decisionLatency = new HashMap<>();
    private final List<Double> makespans = new ArrayList<>();
    private final Map<String, TaskInfo> tasks = new HashMap<>();
    private final Map<String, Integer> dropReasonCounts = new HashMap<>();
    private int totalTasksGenerated = 0;
    private double simStart = 0;
    private double simFinish = 0;

    /** Record a migration event with classification. */
    public void recordMigration(MigrationKind kind, ContainerModule container, String from, String to, double start, double finish, double overheadCpu, double overheadBw, boolean success, String trigger) {
        double migrationTime = Math.max(0, finish - start);
        migrations.add(new MigrationRecord(kind, container.getContainerId(), from, to, start, finish, migrationTime,
                container.getProfile().getContainerSizeMb(), overheadCpu, overheadBw, success, trigger));
    }

    /** Record a fault prediction/occurrence. */
    public void recordFault(String serverName, String faultType, double predictTime, double failTime, double recoveryTime, boolean recovered) {
        faults.add(new FaultRecord(serverName, faultType, predictTime, failTime, recoveryTime, recovered));
    }

    /** Snapshot server state when a fault is predicted. */
    public void recordFaultPrediction(String fogName, String serverName, String faultType, double predictTime, double cpuLoad, double memLoad, double bwLoad, int containers) {
        faultPredictions.add(new FaultPredictionRecord(fogName, serverName, faultType, predictTime, cpuLoad, memLoad, bwLoad, containers));
    }

    /** Record a placement decision (success or failure). */
    public void recordPlacement(String containerId, String fogName, String serverName, double time, boolean success, String reason, List<PlacementServerLoad> loads) {
        placements.add(new PlacementRecord(containerId, fogName, serverName, time, success, reason, loads));
    }

    /** Record a centralized (mode 2) placement decision. */
    public void recordCentralPlacement(String containerId, String targetFog, String targetServer, boolean toCloud, double score, double time) {
        centralizedPlacements.add(new CentralPlacementRecord(containerId, targetFog, targetServer, toCloud, score, time));
    }

    /** Record gossip/control plane overhead. */
    public void recordGossip(String sender, String receiver, double sizeBytes, double timestamp, double cpuLoad, double memLoad, double bwLoad, boolean stale) {
        gossips.add(new GossipRecord(sender, receiver, sizeBytes, timestamp, cpuLoad, memLoad, bwLoad, stale));
        recordNetwork("gossip", sender, receiver, sizeBytes, timestamp);
    }

    /** Record SLA outcome for a task/container. */
    public void recordSla(String containerId, String originatingEdge, String assignedFogNode, double arrivalTime, double completionTime, double deadlineSeconds,
                          boolean slaSuccess, double tExec, double tNet, double tSlack, double tMig, double makespan, int mode) {
        sla.add(new SlaRecord(containerId, originatingEdge, assignedFogNode, arrivalTime, completionTime, deadlineSeconds, slaSuccess, tExec, tNet, tSlack, tMig, makespan, mode));
        makespans.add(makespan);
    }

    /** Record bidding payments/tokens. */
    public void recordPayment(int payerId, int payeeId, double amount) {
        economic.payments.add(new Payment(payerId, payeeId, amount));
    }

    /** Record decision latency for scheduling. */
    public void recordDecisionLatency(String containerId, double started, double finished, boolean centralized) {
        decisionLatency.put(containerId, new DecisionLatency(started, finished, centralized));
    }

    /** Record load per fog node (averaged across servers). */
    public void recordLoad(String fogName, double time, double cpuLoad, double memLoad, double bwLoad, boolean stale) {
        load.add(new LoadRecord(fogName, time, cpuLoad, memLoad, bwLoad, stale));
    }

    /** Record per-server resource usage snapshot. */
    public void recordResource(String fogName, String serverName, double time, double cpuLoad, double memLoad, double bwLoad, int containers) {
        resources.add(new ResourceRecord(fogName, serverName, time, cpuLoad, memLoad, bwLoad, containers));
    }

    /** Record network usage for visualization (bytes over time). */
    public void recordNetwork(String kind, String from, String to, double bytes, double time) {
        network.add(new NetworkRecord(kind, from, to, bytes, time));
    }

    /** Record a bid response for debugging selection behavior. */
    public void recordBid(String containerId, int bidderId, String bidderName, double claimedBw, double claimedMigTime,
                          double bidValue, double effectiveBid, boolean feasible, boolean winner, double trustBefore, double time) {
        bids.add(new BidRecord(containerId, bidderId, bidderName, claimedBw, claimedMigTime, bidValue, effectiveBid, feasible, winner, trustBefore, time));
    }

    /** Mark the winner bid for a container (updates any existing bid rows for that bidder). */
    public void markBidWinner(String containerId, int bidderId) {
        for (int i = 0; i < bids.size(); i++) {
            BidRecord b = bids.get(i);
            if (b.containerId().equals(containerId) && b.bidderId() == bidderId) {
                bids.set(i, new BidRecord(b.containerId(), b.bidderId(), b.bidderName(), b.claimedBw(), b.claimedMigTime(), b.bidValue(), b.effectiveBid(), b.feasible(), true, b.trustBefore(), b.time()));
            }
        }
    }

    /** Record settlement details including honesty/trust updates and payment. */
    public void recordSettlement(SettlementRecord record) {
        settlements.add(record);
    }

    /** Record trust evaluation (claimed vs actual performance). */
    public void recordTrustEvaluation(String containerId, int bidderId, String bidderName, double claimedBw, double actualBw,
                                      double claimedMigTime, double actualMigTime, double errBw, double errMig,
                                      double trustBefore, double trustAfter, boolean dishonest, double time) {
        trustEvaluations.add(new TrustEvaluationRecord(containerId, bidderId, bidderName, claimedBw, actualBw,
                claimedMigTime, actualMigTime, errBw, errMig, trustBefore, trustAfter, dishonest, time));
    }

    public List<MigrationRecord> getMigrations() {
        return migrations;
    }

    public List<FaultRecord> getFaults() {
        return faults;
    }

    public List<FaultPredictionRecord> getFaultPredictions() {
        return faultPredictions;
    }

    public List<PlacementRecord> getPlacements() {
        return placements;
    }

    public List<CentralPlacementRecord> getCentralizedPlacements() {
        return centralizedPlacements;
    }

    public List<GossipRecord> getGossips() {
        return gossips;
    }

    public List<SlaRecord> getSla() {
        return sla;
    }

    public List<LoadRecord> getLoad() {
        return load;
    }

    public List<ResourceRecord> getResources() {
        return resources;
    }

    public List<NetworkRecord> getNetwork() {
        return network;
    }

    public List<Payment> getPayments() {
        return economic.payments;
    }

    public List<BidRecord> getBids() {
        return bids;
    }

    public List<SettlementRecord> getSettlements() {
        return settlements;
    }

    public List<TrustEvaluationRecord> getTrustEvaluations() {
        return trustEvaluations;
    }

    public Map<String, DecisionLatency> getDecisionLatency() {
        return decisionLatency;
    }

    public List<Double> getMakespans() {
        return makespans;
    }

    public void recordTaskGenerated(ContainerModule container, String ownerFog, double timeSeconds) {
        TaskInfo info = new TaskInfo(
                container.getContainerId(),
                container.getOriginatingEdge(),
                ownerFog,
                container.getArrivalTime(),
                container.getDeadlineSeconds(),
                container.getTExecSeconds(),
                container.getTNetSeconds(),
                container.getTSlackSeconds(),
                container.getTMigSeconds());
        info.currentHost = ownerFog;
        info.status = TaskStatus.ARRIVED;
        info.statusDetail = "arrived";
        info.lastUpdateTime = timeSeconds;
        tasks.put(container.getContainerId(), info);
        totalTasksGenerated++;
    }

    public void markTaskRunning(ContainerModule container, String hostName, String detail, double timeSeconds) {
        TaskInfo info = tasks.get(container.getContainerId());
        updateStatus(info, TaskStatus.RUNNING, detail, hostName, false, timeSeconds);
    }

    public void markTaskPending(ContainerModule container, String detail, boolean pending, double timeSeconds) {
        TaskInfo info = tasks.get(container.getContainerId());
        updateStatus(info, pending ? TaskStatus.PENDING : null, detail, null, pending, timeSeconds);
    }

    public void markTaskPending(String containerId, String detail, boolean pending, double timeSeconds) {
        TaskInfo info = tasks.get(containerId);
        updateStatus(info, pending ? TaskStatus.PENDING : null, detail, null, pending, timeSeconds);
    }

    public void recordTaskMigrationAttempt(ContainerModule container, String trigger, double timeSeconds) {
        TaskInfo info = tasks.get(container.getContainerId());
        if (info == null) {
            return;
        }
        info.migrationAttempts++;
        if (trigger != null && !trigger.isBlank()) {
            info.lastMigrationTrigger = trigger;
        }
        info.lastUpdateTime = timeSeconds;
    }

    public void markTaskMigrating(ContainerModule container, String targetHost, String trigger, double timeSeconds) {
        TaskInfo info = tasks.get(container.getContainerId());
        updateStatus(info, TaskStatus.MIGRATING, trigger, targetHost, false, timeSeconds);
    }

    public void markTaskMigrationFailed(ContainerModule container, String reason, double timeSeconds) {
        TaskInfo info = tasks.get(container.getContainerId());
        updateStatus(info, TaskStatus.FAILED, reason, null, true, timeSeconds);
    }

    public void markTaskCompleted(ContainerModule container, double completionTime, String finishHost, boolean slaSuccess) {
        TaskInfo info = tasks.get(container.getContainerId());
        if (info == null) {
            return;
        }
        updateStatus(info, TaskStatus.COMPLETED, finishHost, finishHost, false, completionTime);
        info.completed = true;
        info.completionTime = completionTime;
        info.slaSuccess = slaSuccess;
    }

    public void markTaskDropped(ContainerModule container, String reason, double dropTime) {
        markTaskDropped(container.getContainerId(), reason, dropTime);
    }

    private void markTaskDropped(String containerId, String reason, double dropTime) {
        TaskInfo info = tasks.get(containerId);
        if (info == null || info.dropped) {
            return;
        }
        String normalized = (reason == null || reason.isBlank())
                ? (info.status != null ? info.status.name().toLowerCase() : "unknown")
                : reason;
        updateStatus(info, TaskStatus.DROPPED, normalized, null, false, dropTime);
        info.dropped = true;
        info.dropReason = normalized;
        dropReasonCounts.merge(normalized, 1, Integer::sum);
    }

    private void updateStatus(TaskInfo info, TaskStatus status, String detail, String host, Boolean pending, double timeSeconds) {
        if (info == null) {
            return;
        }
        if (status != null) {
            info.status = status;
        }
        if (detail != null && !detail.isBlank()) {
            info.statusDetail = detail;
        } else if (detail != null) {
            info.statusDetail = "";
        }
        if (host != null && !host.isBlank()) {
            info.currentHost = host;
        }
        if (pending != null) {
            info.pending = pending;
        }
        info.lastUpdateTime = timeSeconds;
    }

    public void finalizeTaskStatuses(double simFinishSeconds) {
        for (TaskInfo info : tasks.values()) {
            if (!info.completed && !info.dropped) {
                markTaskDropped(info.containerId, info.statusDetail, simFinishSeconds);
            }
        }
    }

    public int getTotalTasksGenerated() {
        return totalTasksGenerated;
    }

    public long getCompletedTaskCount() {
        return tasks.values().stream().filter(info -> info.completed).count();
    }

    public long getDroppedTaskCount() {
        return dropReasonCounts.values().stream().mapToLong(Integer::longValue).sum();
    }

    public Map<String, Integer> getTaskDropReasonCounts() {
        return new HashMap<>(dropReasonCounts);
    }

    public List<TaskStatusRecord> getTaskStatusRecords() {
        List<TaskStatusRecord> records = new ArrayList<>();
        for (TaskInfo info : tasks.values()) {
            records.add(info.toStatusRecord());
        }
        return records;
    }

    public List<TaskDropRecord> getDroppedTaskRecords() {
        List<TaskDropRecord> records = new ArrayList<>();
        for (TaskInfo info : tasks.values()) {
            TaskDropRecord record = info.toDropRecord();
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    public void reset() {
        migrations.clear();
        faults.clear();
        faultPredictions.clear();
        placements.clear();
        centralizedPlacements.clear();
        gossips.clear();
        sla.clear();
        load.clear();
        resources.clear();
        network.clear();
        bids.clear();
        settlements.clear();
        trustEvaluations.clear();
        economic.payments.clear();
        decisionLatency.clear();
        makespans.clear();
        tasks.clear();
        dropReasonCounts.clear();
        totalTasksGenerated = 0;
        simStart = 0;
        simFinish = 0;
    }

    public double getSimStart() {
        return simStart;
    }

    public double getSimFinish() {
        return simFinish;
    }

    public void markSimStart(double t) {
        simStart = t;
    }

    public void markSimFinish(double t) {
        simFinish = t;
    }

    /** Export to a simple JSON lines structure for post-processing. */
    public void export(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("migrations.json"), JsonUtil.toJsonLines(migrations));
        Files.writeString(outDir.resolve("faults.json"), JsonUtil.toJsonLines(faults));
        Files.writeString(outDir.resolve("fault_predictions.json"), JsonUtil.toJsonLines(faultPredictions));
        Files.writeString(outDir.resolve("placements.json"), JsonUtil.toJsonLines(placements));
        Files.writeString(outDir.resolve("placement_centralized.json"), JsonUtil.toJsonLines(centralizedPlacements));
        Files.writeString(outDir.resolve("gossip.json"), JsonUtil.toJsonLines(gossips));
        Files.writeString(outDir.resolve("sla.json"), JsonUtil.toJsonLines(sla));
        Files.writeString(outDir.resolve("economic.json"), JsonUtil.toJsonLines(economic.payments));
        Files.writeString(outDir.resolve("decisions.json"), JsonUtil.toJsonLines(decisionLatency.values()));
        Files.writeString(outDir.resolve("network.csv"), JsonUtil.toCsv(network, "time,kind,from,to,bytes"));
        Files.writeString(outDir.resolve("load.csv"), JsonUtil.toCsv(load, "time,fog,cpuLoad,memLoad,bwLoad,stale"));
        Files.writeString(outDir.resolve("resources.csv"), JsonUtil.toCsv(resources, "time,fog,server,containers,cpuLoad,memLoad,bwLoad"));
        Files.writeString(outDir.resolve("bids.csv"), JsonUtil.toCsv(bids, "time,containerId,bidderId,bidderName,claimedBw,claimedMigTime,bidValue,effectiveBid,feasible,winner,trustBefore"));
        Files.writeString(outDir.resolve("payment_log.csv"), JsonUtil.toCsv(settlements, "time,containerId,bidderId,bidderName,claimedBw,claimedMigTime,actualBw,actualMigTime,errBw,errMig,trustBefore,trustAfter,bidValue,effectiveBid,payment,slaMet"));
        Files.writeString(outDir.resolve("trust_evaluations.csv"), JsonUtil.toCsv(trustEvaluations, "time,containerId,bidderId,bidderName,claimedBw,actualBw,claimedMigTime,actualMigTime,errBw,errMig,trustBefore,trustAfter,dishonest"));
        Files.writeString(outDir.resolve("sla_makespan_metrics.csv"), JsonUtil.toCsv(sla,
                "taskId,originatingEdge,assignedFogNode,arrivalTime,completionTime,deadline,slaSuccess,T_exec,T_net,T_slack,T_mig,makespan,makespanSeconds,mode"));
        Files.writeString(outDir.resolve("task_status.csv"), JsonUtil.toCsv(
                getTaskStatusRecords(),
                "containerId,originatingEdge,ownerFog,currentHost,arrivalTime,deadline,lastUpdateTime,status,statusDetail,dropReason,migrationAttempts,lastMigrationTrigger,isPending,T_exec,T_net,T_slack,T_mig,completionTime,slaSuccess"));
        Files.writeString(outDir.resolve("dropped_tasks.csv"), JsonUtil.toCsv(
                getDroppedTaskRecords(),
                "containerId,originatingEdge,ownerFog,currentHost,arrivalTime,dropTime,deadline,dropReason,lastMigrationTrigger,migrationAttempts,isPending,T_exec,T_net,T_slack,T_mig"));
    }

    public enum MigrationKind { INTRA_FOG, INTER_FOG, CLOUD }

    public record MigrationRecord(MigrationKind kind, String containerId, String from, String to, double start,
                                  double finish, double migrationTime, double sizeMb, double overheadCpu, double overheadBw,
                                  boolean success, String reason) { }

    public record FaultRecord(String serverName, String faultType, double predictedAt, double failedAt, double recoveryAt, boolean recovered) { }

    public record FaultPredictionRecord(String fogName, String serverName, String faultType, double predictedAt, double cpuLoad, double memLoad, double bwLoad, int containers) { }

    public record PlacementRecord(String containerId, String fogName, String serverName, double time, boolean success, String reason, List<PlacementServerLoad> loads) { }

    public record CentralPlacementRecord(String containerId, String targetFog, String targetServer, boolean toCloud, double score, double time) { }

    public record PlacementServerLoad(String serverName, double cpuLoad, double memLoad, double bwLoad, int containers, boolean predictedFault) { }

    public record GossipRecord(String sender, String receiver, double bytes, double timestamp, double cpuLoad, double memLoad, double bwLoad, boolean stale) { }

    public record SlaRecord(String containerId, String originatingEdge, String assignedFogNode, double arrivalTime, double completionTime, double deadlineSeconds,
                            boolean slaSuccess, double tExec, double tNet, double tSlack, double tMig, double makespan, int mode) { }

    public record Payment(int payerId, int payeeId, double amount) { }

    public record EconomicRecord(List<Payment> payments) {
        public EconomicRecord() { this(new ArrayList<>()); }
    }

    public record DecisionLatency(double started, double finished, boolean centralized) { }

    public record LoadRecord(String fogName, double time, double cpuLoad, double memLoad, double bwLoad, boolean stale) { }

    public record ResourceRecord(String fogName, String serverName, double time, double cpuLoad, double memLoad, double bwLoad, int containers) { }

    public record NetworkRecord(String kind, String from, String to, double bytes, double time) { }

    public record BidRecord(String containerId, int bidderId, String bidderName, double claimedBw, double claimedMigTime,
                            double bidValue, double effectiveBid, boolean feasible, boolean winner, double trustBefore, double time) { }

    public record SettlementRecord(String containerId, int bidderId, String bidderName, double claimedBw, double claimedMigTime,
                                   double actualBw, double actualMigTime, double errBw, double errMig,
                                   double trustBefore, double trustAfter, double bidValue, double effectiveBid,
                                   double payment, boolean slaMet, double time) { }

    public record TrustEvaluationRecord(String containerId, int bidderId, String bidderName, double claimedBw, double actualBw,
                                        double claimedMigTime, double actualMigTime, double errBw, double errMig,
                                        double trustBefore, double trustAfter, boolean dishonest, double time) { }

    public enum TaskStatus { ARRIVED, RUNNING, PENDING, MIGRATING, FAILED, COMPLETED, DROPPED }

    private static final class TaskInfo {
        private final String containerId;
        private final String originatingEdge;
        private final String ownerFog;
        private final double arrivalTime;
        private final double deadline;
        private final double tExec;
        private final double tNet;
        private final double tSlack;
        private final double tMig;
        private String currentHost;
        private TaskStatus status = TaskStatus.ARRIVED;
        private String statusDetail = "";
        private double lastUpdateTime;
        private boolean pending;
        private int migrationAttempts;
        private String lastMigrationTrigger = "";
        private boolean completed;
        private double completionTime = -1;
        private boolean slaSuccess;
        private boolean dropped;
        private String dropReason = "";

        TaskInfo(String containerId, String originatingEdge, String ownerFog, double arrivalTime, double deadline,
                 double tExec, double tNet, double tSlack, double tMig) {
            this.containerId = containerId;
            this.originatingEdge = originatingEdge;
            this.ownerFog = ownerFog;
            this.arrivalTime = arrivalTime;
            this.deadline = deadline;
            this.tExec = tExec;
            this.tNet = tNet;
            this.tSlack = tSlack;
            this.tMig = tMig;
            this.currentHost = ownerFog;
        }

        TaskStatusRecord toStatusRecord() {
            return new TaskStatusRecord(containerId, originatingEdge, ownerFog, currentHost, arrivalTime, deadline,
                    lastUpdateTime, status, statusDetail, dropReason, migrationAttempts, lastMigrationTrigger,
                    pending, tExec, tNet, tSlack, tMig, completionTime, slaSuccess);
        }

        TaskDropRecord toDropRecord() {
            if (!dropped) {
                return null;
            }
            return new TaskDropRecord(containerId, originatingEdge, ownerFog, currentHost, arrivalTime, lastUpdateTime,
                    deadline, dropReason, lastMigrationTrigger, migrationAttempts, pending, tExec, tNet, tSlack, tMig);
        }
    }

    public record TaskStatusRecord(String containerId, String originatingEdge, String ownerFog, String currentHost,
                                   double arrivalTime, double deadline, double lastUpdateTime, TaskStatus status,
                                   String statusDetail, String dropReason, int migrationAttempts,
                                   String lastMigrationTrigger, boolean pending, double tExec, double tNet, double tSlack,
                                   double tMig, double completionTime, boolean slaSuccess) { }

    public record TaskDropRecord(String containerId, String originatingEdge, String ownerFog, String currentHost,
                                 double arrivalTime, double dropTime, double deadline, String dropReason,
                                 String lastMigrationTrigger, int migrationAttempts, boolean pending,
                                 double tExec, double tNet, double tSlack, double tMig) { }

    /** Minimal JSON serializer for structured metrics lines. */
    public static final class JsonUtil {
        public static String toJsonLines(Iterable<?> records) {
            StringBuilder sb = new StringBuilder();
            for (Object o : records) {
                sb.append(toJsonObject(o)).append("\n");
            }
            return sb.toString();
        }

        public static String toJsonObject(Object o) {
            if (o == null) return "null";
            if (o instanceof Number || o instanceof Boolean) {
                return o.toString();
            }
            if (o instanceof String s) {
                return "\"" + s + "\"";
            }
            if (o instanceof Map<?, ?> m) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (var e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(e.getKey()).append('"').append(':').append(toJsonObject(e.getValue()));
                }
                sb.append('}');
                return sb.toString();
            }
            if (o instanceof Iterable<?> it) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object v : it) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(toJsonObject(v));
                }
                sb.append(']');
                return sb.toString();
            }
            if (o.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(o);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(toJsonObject(java.lang.reflect.Array.get(o, i)));
                }
                sb.append(']');
                return sb.toString();
            }
            if (o.getClass().isRecord()) {
                Map<String, Object> map = new HashMap<>();
                for (var field : o.getClass().getRecordComponents()) {
                    try {
                        map.put(field.getName(), field.getAccessor().invoke(o));
                    } catch (Exception ignored) {
                    }
                }
                return toJsonObject(map);
            }
            return "\"" + String.valueOf(o) + "\"";
        }

        public static String toCsv(List<?> rows, String header) {
            StringBuilder sb = new StringBuilder(header).append("\n");
            for (Object row : rows) {
                if (row instanceof NetworkRecord n) {
                    sb.append(n.time()).append(',').append(n.kind()).append(',').append(n.from()).append(',').append(n.to()).append(',').append(n.bytes()).append("\n");
                } else if (row instanceof LoadRecord l) {
                    sb.append(l.time()).append(',').append(l.fogName()).append(',').append(l.cpuLoad()).append(',').append(l.memLoad()).append(',').append(l.bwLoad()).append(',').append(l.stale()).append("\n");
                } else if (row instanceof ResourceRecord r) {
                    sb.append(r.time()).append(',').append(r.fogName()).append(',').append(r.serverName()).append(',').append(r.containers()).append(',').append(r.cpuLoad()).append(',').append(r.memLoad()).append(',').append(r.bwLoad()).append("\n");
                } else if (row instanceof BidRecord b) {
                    sb.append(b.time()).append(',').append(b.containerId()).append(',').append(b.bidderId()).append(',').append(b.bidderName()).append(',')
                            .append(b.claimedBw()).append(',').append(b.claimedMigTime()).append(',').append(b.bidValue()).append(',')
                            .append(b.effectiveBid()).append(',').append(b.feasible()).append(',').append(b.winner()).append(',').append(b.trustBefore()).append("\n");
                } else if (row instanceof SettlementRecord s) {
                    sb.append(s.time()).append(',').append(s.containerId()).append(',').append(s.bidderId()).append(',').append(s.bidderName()).append(',')
                            .append(s.claimedBw()).append(',').append(s.claimedMigTime()).append(',').append(s.actualBw()).append(',').append(s.actualMigTime()).append(',')
                            .append(s.errBw()).append(',').append(s.errMig()).append(',').append(s.trustBefore()).append(',').append(s.trustAfter()).append(',')
                            .append(s.bidValue()).append(',').append(s.effectiveBid()).append(',').append(s.payment()).append(',').append(s.slaMet()).append("\n");
                } else if (row instanceof TrustEvaluationRecord t) {
                    sb.append(t.time()).append(',').append(t.containerId()).append(',').append(t.bidderId()).append(',').append(t.bidderName()).append(',')
                            .append(t.claimedBw()).append(',').append(t.actualBw()).append(',').append(t.claimedMigTime()).append(',').append(t.actualMigTime()).append(',')
                            .append(t.errBw()).append(',').append(t.errMig()).append(',').append(t.trustBefore()).append(',').append(t.trustAfter()).append(',').append(t.dishonest()).append("\n");
                } else if (row instanceof SlaRecord s) {
                    sb.append(s.containerId()).append(',').append(s.originatingEdge()).append(',').append(s.assignedFogNode()).append(',')
                            .append(s.arrivalTime()).append(',').append(s.completionTime()).append(',').append(s.deadlineSeconds()).append(',')
                            .append(s.slaSuccess()).append(',').append(s.tExec()).append(',').append(s.tNet()).append(',').append(s.tSlack()).append(',')
                            .append(s.tMig()).append(',').append(s.makespan()).append(',').append(s.makespan()).append(',').append(s.mode()).append("\n");
                } else if (row instanceof TaskStatusRecord t) {
                    sb.append(t.containerId()).append(',').append(t.originatingEdge()).append(',').append(t.ownerFog()).append(',')
                            .append(t.currentHost()).append(',').append(t.arrivalTime()).append(',').append(t.deadline()).append(',')
                            .append(t.lastUpdateTime()).append(',').append(t.status() != null ? t.status().name().toLowerCase() : "").append(',')
                            .append(t.statusDetail()).append(',').append(t.dropReason()).append(',').append(t.migrationAttempts()).append(',')
                            .append(t.lastMigrationTrigger()).append(',').append(t.pending()).append(',')
                            .append(t.tExec()).append(',').append(t.tNet()).append(',').append(t.tSlack()).append(',').append(t.tMig()).append(',')
                            .append(t.completionTime()).append(',').append(t.slaSuccess()).append("\n");
                } else if (row instanceof TaskDropRecord d) {
                    sb.append(d.containerId()).append(',').append(d.originatingEdge()).append(',').append(d.ownerFog()).append(',')
                            .append(d.currentHost()).append(',').append(d.arrivalTime()).append(',').append(d.dropTime()).append(',')
                            .append(d.deadline()).append(',').append(d.dropReason()).append(',').append(d.lastMigrationTrigger()).append(',')
                            .append(d.migrationAttempts()).append(',').append(d.pending()).append(',')
                            .append(d.tExec()).append(',').append(d.tNet()).append(',').append(d.tSlack()).append(',').append(d.tMig()).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
