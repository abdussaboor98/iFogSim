package org.collabft.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds summary.json and plots/ placeholders as specified in METRICS.md.
 */
public final class MetricsSummarizer {
    private MetricsSummarizer() {
    }

    public static void summarize(MetricsCollector collector, Path resultsDir) throws IOException {
        Files.createDirectories(resultsDir);
        Map<String, Object> summary = new HashMap<>();

        summary.put("migrationTotals", migrationSummary(collector));
        summary.put("faultTolerance", faultSummary(collector));
        summary.put("network", networkSummary(collector));
        summary.put("sla", slaSummary(collector));
        summary.put("economic", economicSummary(collector));
        summary.put("scheduling", schedulingSummary(collector));
        summary.put("availability", availabilitySummary(collector));
        summary.put("makespan", collector.getSimFinish() - collector.getSimStart());
        summary.put("loadImbalance", loadImbalance(collector));

        Files.writeString(resultsDir.resolve("summary.json"), MetricsCollector.JsonUtil.toJsonObject(summary));

        // Create plots/ placeholder for downstream visualization
        Files.createDirectories(resultsDir.resolve("plots"));
        Files.writeString(resultsDir.resolve("plots/README.txt"), "Place generated plots here (not produced in-sim).");
    }

    private static Map<String, Object> migrationSummary(MetricsCollector collector) {
        Map<String, Object> m = new HashMap<>();
        long intra = collector.getMigrations().stream().filter(r -> r.kind() == MetricsCollector.MigrationKind.INTRA_FOG).count();
        long inter = collector.getMigrations().stream().filter(r -> r.kind() == MetricsCollector.MigrationKind.INTER_FOG).count();
        long cloud = collector.getMigrations().stream().filter(r -> r.kind() == MetricsCollector.MigrationKind.CLOUD).count();
        double avgTime = collector.getMigrations().stream().mapToDouble(MetricsCollector.MigrationRecord::migrationTime).average().orElse(0);
        long successes = collector.getMigrations().stream().filter(MetricsCollector.MigrationRecord::success).count();
        long total = collector.getMigrations().size();
        m.put("total", total);
        m.put("intra", intra);
        m.put("inter", inter);
        m.put("cloud", cloud);
        m.put("avgTime", avgTime);
        m.put("successRate", total == 0 ? 0 : (double) successes / total);
        return m;
    }

    private static Map<String, Object> faultSummary(MetricsCollector collector) {
        Map<String, Object> f = new HashMap<>();
        double avgLead = collector.getFaults().stream().mapToDouble(r -> r.failedAt() - r.predictedAt()).average().orElse(0);
        long recovered = collector.getFaults().stream().filter(MetricsCollector.FaultRecord::recovered).count();
        f.put("count", collector.getFaults().size());
        f.put("avgLeadTime", avgLead);
        f.put("recovered", recovered);
        return f;
    }

    private static Map<String, Object> networkSummary(MetricsCollector collector) {
        Map<String, Object> n = new HashMap<>();
        double gossipBytes = collector.getGossips().stream().mapToDouble(MetricsCollector.GossipRecord::bytes).sum();
        n.put("gossipMessages", collector.getGossips().size());
        n.put("gossipBytes", gossipBytes);
        return n;
    }

    private static Map<String, Object> slaSummary(MetricsCollector collector) {
        Map<String, Object> s = new HashMap<>();
        long violations = collector.getSla().stream().filter(r -> !r.slaMet()).count();
        long total = collector.getSla().size();
        double avgLatency = collector.getSla().stream().mapToDouble(MetricsCollector.SlaRecord::completionLatency).average().orElse(0);
        double avgSlaValue = collector.getSla().stream().mapToDouble(MetricsCollector.SlaRecord::slaValue).average().orElse(0);
        s.put("count", total);
        s.put("violations", violations);
        s.put("violationRatio", total == 0 ? 0 : (double) violations / total);
        s.put("avgCompletionLatency", avgLatency);
        s.put("avgSlaValue", avgSlaValue);
        return s;
    }

    private static Map<String, Object> economicSummary(MetricsCollector collector) {
        Map<String, Object> e = new HashMap<>();
        double totalPayments = collector.getPayments().stream().mapToDouble(MetricsCollector.Payment::amount).sum();
        e.put("paymentsCount", collector.getPayments().size());
        e.put("paymentsTotal", totalPayments);
        return e;
    }

    private static Map<String, Object> schedulingSummary(MetricsCollector collector) {
        Map<String, Object> s = new HashMap<>();
        double fogLatency = collector.getDecisionLatency().values().stream().filter(dl -> !dl.centralized()).mapToDouble(dl -> dl.finished() - dl.started()).average().orElse(0);
        double cloudLatency = collector.getDecisionLatency().values().stream().filter(MetricsCollector.DecisionLatency::centralized).mapToDouble(dl -> dl.finished() - dl.started()).average().orElse(0);
        s.put("fogDecisionLatencyAvg", fogLatency);
        s.put("cloudDecisionLatencyAvg", cloudLatency);
        s.put("decisionsCount", collector.getDecisionLatency().size());
        return s;
    }

    private static Map<String, Object> availabilitySummary(MetricsCollector collector) {
        Map<String, Object> a = new HashMap<>();
        long recovered = collector.getFaults().stream().filter(MetricsCollector.FaultRecord::recovered).count();
        long totalFaults = collector.getFaults().size();
        a.put("faults", totalFaults);
        a.put("recovered", recovered);
        a.put("availabilityRatio", totalFaults == 0 ? 1.0 : (double) recovered / totalFaults);
        return a;
    }

    private static Map<String, Object> loadImbalance(MetricsCollector collector) {
        Map<String, Object> l = new HashMap<>();
        Map<String, Long> perFog = new HashMap<>();
        for (MetricsCollector.MigrationRecord r : collector.getMigrations()) {
            perFog.put(r.from(), perFog.getOrDefault(r.from(), 0L) + 1);
        }
        double mean = perFog.values().stream().mapToDouble(Long::doubleValue).average().orElse(0);
        double variance = perFog.values().stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        l.put("stddev", Math.sqrt(variance));
        l.put("meanMigrations", mean);
        return l;
    }
}
