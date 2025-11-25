package org.collabft.model;

import org.collabft.metrics.MetricsCollector;

/**
 * Result of a migration sent back to the origin for logging.
 */
public class MigrationResult {
    private final ContainerModule container;
    private final MetricsCollector.MigrationKind kind;
    private final double start;
    private final double finish;
    private final double overheadCpu;
    private final double overheadBw;
    private final boolean success;
    private final String trigger;

    public MigrationResult(ContainerModule container, MetricsCollector.MigrationKind kind, double start, double finish, double overheadCpu, double overheadBw, boolean success, String trigger) {
        this.container = container;
        this.kind = kind;
        this.start = start;
        this.finish = finish;
        this.overheadCpu = overheadCpu;
        this.overheadBw = overheadBw;
        this.success = success;
        this.trigger = trigger;
    }

    public ContainerModule getContainer() {
        return container;
    }

    public MetricsCollector.MigrationKind getKind() {
        return kind;
    }

    public double getStart() {
        return start;
    }

    public double getFinish() {
        return finish;
    }

    public double getOverheadCpu() {
        return overheadCpu;
    }

    public double getOverheadBw() {
        return overheadBw;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getTrigger() {
        return trigger;
    }
}
