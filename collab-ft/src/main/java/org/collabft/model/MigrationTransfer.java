package org.collabft.model;

import org.collabft.metrics.MetricsCollector;

/**
 * Payload for executing a migration to a target node.
 */
public class MigrationTransfer {
    private final ContainerModule container;
    private final int originId;
    private final MetricsCollector.MigrationKind kind;
    private final double linkBandwidthMbps;
    private final String trigger;
    private final double latencySeconds;

    public MigrationTransfer(ContainerModule container, int originId, MetricsCollector.MigrationKind kind, double linkBandwidthMbps, double latencySeconds, String trigger) {
        this.container = container;
        this.originId = originId;
        this.kind = kind;
        this.linkBandwidthMbps = linkBandwidthMbps;
        this.trigger = trigger;
        this.latencySeconds = latencySeconds;
    }

    public ContainerModule getContainer() {
        return container;
    }

    public int getOriginId() {
        return originId;
    }

    public MetricsCollector.MigrationKind getKind() {
        return kind;
    }

    public double getLinkBandwidthMbps() {
        return linkBandwidthMbps;
    }

    public String getTrigger() {
        return trigger;
    }

    public double getLatencySeconds() {
        return latencySeconds;
    }
}
