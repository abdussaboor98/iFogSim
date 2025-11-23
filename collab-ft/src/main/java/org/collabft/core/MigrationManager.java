package org.collabft.core;

import org.collabft.config.SimulationConfig;
import org.collabft.model.ContainerProfile;
import org.collabft.model.MigrationLog;

import java.util.Objects;

/**
 * Encapsulates migration timing helpers and event construction for container moves.
 */
public class MigrationManager {

    private final SimulationConfig config;

    public MigrationManager(SimulationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public double estimateRunDuration(ContainerProfile profile) {
        return Math.max(1.0, profile.getDeadlineSec() * 0.5);
    }

    public double transferDuration(ContainerProfile profile, MigrationLog.Phase phase) {
        double bw = config.getTopology().getServerBw();
        double factor = switch (phase) {
            case LOCAL -> 1.0;
            case INTER_FOG -> 0.8;
            case CLOUD -> config.getEconomics().getCloudBandwidthFactor();
        };
        double effectiveBw = Math.max(1.0, bw * factor);
        return profile.getSizeMb() / effectiveBw + 0.1 * profile.getSizeMb() / effectiveBw;
    }

    public double bidMigrationCost(ContainerProfile profile, MigrationLog.Phase phase) {
        double bw = Math.max(1.0, effectiveBandwidth(phase));
        double transfer = profile.getSizeMb() / bw;
        double restore = 0.1 * profile.getSizeMb();
        return transfer + restore;
    }

    public double effectiveBandwidth(MigrationLog.Phase phase) {
        double bw = config.getTopology().getServerBw();
        return switch (phase) {
            case LOCAL -> bw;
            case INTER_FOG -> bw * 0.8;
            case CLOUD -> bw * config.getEconomics().getCloudBandwidthFactor();
        };
    }
}
