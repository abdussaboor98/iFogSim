package org.collabft.metrics;

import org.collabft.model.ContainerModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    private final EconomicRecord economic = new EconomicRecord();
    private final Map<String, DecisionLatency> decisionLatency = new HashMap<>();
    private double simStart = 0;
    private double simFinish = 0;

    /** Record a migration event with classification. */
    public void recordMigration(MigrationKind kind, ContainerModule container, double start, double finish, double overheadCpu, double overheadBw, boolean success, String trigger) {
        migrations.add(new MigrationRecord(kind, container.getContainerId(), container.getOwnerFog(), start, finish, overheadCpu, overheadBw, success, trigger));
    }

    /** Record a fault prediction/occurrence. */
    public void recordFault(String serverName, double predictTime, double failTime, boolean recovered) {
        faults.add(new FaultRecord(serverName, predictTime, failTime, recovered));
    }

    /** Record gossip/control plane overhead. */
    public void recordGossip(int senderId, int receiverId, double sizeBytes, double timestamp) {
        gossips.add(new GossipRecord(senderId, receiverId, sizeBytes, timestamp));
    }

    /** Record SLA outcome for a task/container. */
    public void recordSla(String containerId, boolean violated, double completionLatency, double deadlineSeconds) {
        sla.add(new SlaRecord(containerId, violated, completionLatency, deadlineSeconds));
    }

    /** Record bidding payments/tokens. */
    public void recordPayment(int payerId, int payeeId, double amount) {
        economic.payments.add(new Payment(payerId, payeeId, amount));
    }

    /** Record decision latency for scheduling. */
    public void recordDecisionLatency(String containerId, double started, double finished, boolean centralized) {
        decisionLatency.put(containerId, new DecisionLatency(started, finished, centralized));
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

    public List<Payment> getPayments() {
        return economic.payments;
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
    }

    public enum MigrationKind { INTRA_FOG, INTER_FOG, CLOUD }

    public record MigrationRecord(MigrationKind kind, String containerId, String sourceFog, double start,
                                  double finish, double overheadCpu, double overheadBw, boolean success, String trigger) { }

    public record FaultRecord(String serverName, double predictedAt, double failedAt, boolean recovered) { }

    public record GossipRecord(int senderId, int receiverId, double bytes, double timestamp) { }

    public record SlaRecord(String containerId, boolean violated, double completionLatency, double deadlineSeconds) { }

    public record Payment(int payerId, int payeeId, double amount) { }

    public record EconomicRecord(List<Payment> payments) {
        public EconomicRecord() { this(new ArrayList<>()); }
    }

    public record DecisionLatency(double started, double finished, boolean centralized) { }

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
            Map<String, Object> map = new HashMap<>();
            if (o instanceof Map<?, ?> m) {
                for (var e : m.entrySet()) {
                    map.put(String.valueOf(e.getKey()), e.getValue());
                }
            } else if (o.getClass().isRecord()) {
                for (var field : o.getClass().getRecordComponents()) {
                    try {
                        map.put(field.getName(), field.getAccessor().invoke(o));
                    } catch (Exception ignored) {
                    }
                }
            } else {
                map.put("value", o.toString());
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':');
                Object v = e.getValue();
                if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v);
                } else {
                    sb.append('"').append(String.valueOf(v)).append('"');
                }
            }
            sb.append('}');
            return sb.toString();
        }
    }
}
