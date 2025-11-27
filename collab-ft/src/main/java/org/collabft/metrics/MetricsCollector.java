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
    private final List<GossipRecord> gossips = new ArrayList<>();
    private final List<SlaRecord> sla = new ArrayList<>();
    private final List<LoadRecord> load = new ArrayList<>();
    private final List<ResourceRecord> resources = new ArrayList<>();
    private final List<NetworkRecord> network = new ArrayList<>();
    private final EconomicRecord economic = new EconomicRecord();
    private final List<DroppedRecord> dropped = new ArrayList<>();
    private final Map<String, DecisionLatency> decisionLatency = new HashMap<>();
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

    /** Mark a previously recorded fault as recovered at the given time. */
    public void markRecovered(String serverName, double recoveryAt) {
        for (int i = faults.size() - 1; i >= 0; i--) {
            FaultRecord fr = faults.get(i);
            if (fr.serverName().equals(serverName) && !fr.recovered()) {
                faults.set(i, new FaultRecord(fr.serverName(), fr.faultType(), fr.predictedAt(), fr.failedAt(), recoveryAt, true));
                break;
            }
        }
    }

    /** Record gossip/control plane overhead. */
    public void recordGossip(String sender, String receiver, double sizeBytes, double timestamp, double cpuLoad, double memLoad, double bwLoad, boolean stale) {
        gossips.add(new GossipRecord(sender, receiver, sizeBytes, timestamp, cpuLoad, memLoad, bwLoad, stale));
        recordNetwork("gossip", sender, receiver, sizeBytes, timestamp);
    }

    /** Record SLA outcome for a task/container. */
    public void recordSla(String containerId, boolean violated, double completionLatency, double deadlineSeconds, double slaValue, double payment) {
        sla.add(new SlaRecord(containerId, !violated, completionLatency, deadlineSeconds, slaValue, payment));
    }

    /** Record a dropped/missed task that could not be migrated. */
    public void recordDropped(String containerId, String reason, double time, String ownerFog) {
        dropped.add(new DroppedRecord(containerId, reason, time, ownerFog));
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

    public List<MigrationRecord> getMigrations() {
        return migrations;
    }

    public List<FaultRecord> getFaults() {
        return faults;
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

    public List<DroppedRecord> getDropped() {
        return dropped;
    }

    public Map<String, DecisionLatency> getDecisionLatency() {
        return decisionLatency;
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
        Files.writeString(outDir.resolve("gossip.json"), JsonUtil.toJsonLines(gossips));
        Files.writeString(outDir.resolve("sla.json"), JsonUtil.toJsonLines(sla));
        Files.writeString(outDir.resolve("economic.json"), JsonUtil.toJsonLines(economic.payments));
        Files.writeString(outDir.resolve("decisions.json"), JsonUtil.toJsonLines(decisionLatency.values()));
        Files.writeString(outDir.resolve("network.csv"), JsonUtil.toCsv(network, "time,kind,from,to,bytes"));
        Files.writeString(outDir.resolve("load.csv"), JsonUtil.toCsv(load, "time,fog,cpuLoad,memLoad,bwLoad,stale"));
        Files.writeString(outDir.resolve("resources.csv"), JsonUtil.toCsv(resources, "time,fog,server,containers,cpuLoad,memLoad,bwLoad"));
        Files.writeString(outDir.resolve("dropped.json"), JsonUtil.toJsonLines(dropped));
    }

    public enum MigrationKind { INTRA_FOG, INTER_FOG, CLOUD }

    public record MigrationRecord(MigrationKind kind, String containerId, String from, String to, double start,
                                  double finish, double migrationTime, double sizeMb, double overheadCpu, double overheadBw,
                                  boolean success, String reason) { }

    public record FaultRecord(String serverName, String faultType, double predictedAt, double failedAt, double recoveryAt, boolean recovered) { }

    public record GossipRecord(String sender, String receiver, double bytes, double timestamp, double cpuLoad, double memLoad, double bwLoad, boolean stale) { }

    public record SlaRecord(String containerId, boolean slaMet, double completionLatency, double deadlineSeconds, double slaValue, double payment) { }

    public record Payment(int payerId, int payeeId, double amount) { }

    public record EconomicRecord(List<Payment> payments) {
        public EconomicRecord() { this(new ArrayList<>()); }
    }

    public record DroppedRecord(String containerId, String reason, double time, String ownerFog) { }

    public record DecisionLatency(double started, double finished, boolean centralized) { }

    public record LoadRecord(String fogName, double time, double cpuLoad, double memLoad, double bwLoad, boolean stale) { }

    public record ResourceRecord(String fogName, String serverName, double time, double cpuLoad, double memLoad, double bwLoad, int containers) { }

    public record NetworkRecord(String kind, String from, String to, double bytes, double time) { }

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
                }
            }
            return sb.toString();
        }
    }
}
