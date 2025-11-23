package org.collabft.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.collabft.model.MigrationLog;
import org.collabft.model.PaymentReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates lightweight metrics for the collab-ft runs and flushes them to disk.
 * Keeps the footprint small while surfacing key health indicators.
 */
public final class MetricsSink {

    private static final MetricsSink INSTANCE = new MetricsSink();

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path outDir = Path.of("results", "collab-ft", "metrics");
    private final Path paymentsDir = Path.of("results", "collab-ft", "payments");
    private final List<MigrationLog> migrations = new ArrayList<>();
    private final List<Double> faultRecoveryTimes = new ArrayList<>();
    private final List<PaymentReport> payments = new ArrayList<>();
    private long gossipBytes = 0L;
    private long bidBytes = 0L;

    private MetricsSink() {
        try {
            Files.createDirectories(outDir);
            Files.createDirectories(paymentsDir);
        } catch (IOException e) {
            // Metrics should not crash the simulation
        }
    }

    public static MetricsSink get() {
        return INSTANCE;
    }

    public synchronized void recordMigration(MigrationLog log) {
        if (log != null) {
            migrations.add(log);
        }
    }

    public synchronized void recordGossipBytes(long bytes) {
        gossipBytes += Math.max(0L, bytes);
    }

    public synchronized void recordBidBytes(long bytes) {
        bidBytes += Math.max(0L, bytes);
    }

    public synchronized void recordFaultRecovery(double seconds) {
        if (seconds > 0) {
            faultRecoveryTimes.add(seconds);
        }
    }

    public synchronized void recordPayment(PaymentReport report) {
        if (report != null) {
            payments.add(report);
        }
    }

    public synchronized void flush(double simulationEndTime) {
        writeMigrationsCsv();
        writePayments();
        writeSummary(simulationEndTime);
    }

    public synchronized Map<String, Object> snapshot(double simulationEndTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("migrationCount", migrations.size());
        payload.put("meanMigrationTime", meanMigrationTime());
        payload.put("slaViolationRatio", slaViolationRatio());
        payload.put("networkOverheadBytes", gossipBytes + bidBytes);
        payload.put("gossipBytes", gossipBytes);
        payload.put("bidBytes", bidBytes);
        payload.put("meanRecoveryTime", mean(faultRecoveryTimes));
        payload.put("makespan", makespan(simulationEndTime));
        payload.put("energyEstimate", energyEstimate());
        payload.put("availability", availability(simulationEndTime));
        payload.put("paymentCount", payments.size());
        return payload;
    }

    private void writeMigrationsCsv() {
        Path csv = outDir.resolve("migrations.csv");
        String header = "containerId,sourceFog,targetFog,sourceServer,targetServer,phase,startedAt,completedAt,success,deadlineMet,failureReason\n";
        StringBuilder sb = new StringBuilder(header);
        for (MigrationLog log : migrations) {
            sb.append(log.getContainerId()).append(',')
                    .append(log.getSourceFogId()).append(',')
                    .append(log.getTargetFogId()).append(',')
                    .append(log.getSourceServerId()).append(',')
                    .append(log.getTargetServerId()).append(',')
                    .append(log.getPhase()).append(',')
                    .append(log.getStartedAt()).append(',')
                    .append(log.getCompletedAt()).append(',')
                    .append(log.isSuccess()).append(',')
                    .append(log.isDeadlineMet()).append(',')
                    .append(safe(log.getFailureReason()))
                    .append('\n');
        }
        try {
            Files.writeString(csv, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // ignore
        }
    }

    private void writeSummary(double simulationEndTime) {
        Path summary = outDir.resolve("summary.json");
        Map<String, Object> payload = new HashMap<>();
        payload.put("migrationCount", migrations.size());
        payload.put("meanMigrationTime", meanMigrationTime());
        payload.put("slaViolationRatio", slaViolationRatio());
        payload.put("networkOverheadBytes", gossipBytes + bidBytes);
        payload.put("gossipBytes", gossipBytes);
        payload.put("bidBytes", bidBytes);
        payload.put("meanRecoveryTime", mean(faultRecoveryTimes));
        payload.put("makespan", makespan(simulationEndTime));
        payload.put("energyEstimate", energyEstimate());
        payload.put("availability", availability(simulationEndTime));
        payload.put("paymentCount", payments.size());
        try {
            Files.writeString(summary, mapper.writeValueAsString(payload),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // ignore
        }
    }

    private double meanMigrationTime() {
        double sum = 0.0;
        int count = 0;
        for (MigrationLog log : migrations) {
            if (log.isSuccess()) {
                sum += log.duration();
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double slaViolationRatio() {
        if (migrations.isEmpty()) {
            return 0.0;
        }
        int violations = 0;
        for (MigrationLog log : migrations) {
            if (!log.isDeadlineMet()) {
                violations++;
            }
        }
        return (double) violations / migrations.size();
    }

    private double makespan(double simulationEndTime) {
        double latest = 0.0;
        for (MigrationLog log : migrations) {
            latest = Math.max(latest, log.getCompletedAt());
        }
        return Math.max(latest, simulationEndTime);
    }

    private double energyEstimate() {
        // Simple proxy: longer migrations imply higher energy; scale lightly to avoid skew.
        return meanMigrationTime() * migrations.size();
    }

    private void writePayments() {
        Path jsonl = paymentsDir.resolve("payments.jsonl");
        StringBuilder sb = new StringBuilder();
        try {
            for (PaymentReport report : payments) {
                sb.append(mapper.writeValueAsString(report)).append(System.lineSeparator());
            }
            Files.writeString(jsonl, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // ignore
        }
    }

    private double availability(double simulationEndTime) {
        double downTime = 0.0;
        for (double rec : faultRecoveryTimes) {
            downTime += rec;
        }
        if (simulationEndTime <= 0) {
            return 1.0;
        }
        double availability = 1.0 - downTime / simulationEndTime;
        return Math.max(0.0, Math.min(1.0, availability));
    }

    private double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }
}
