package org.fog.entities;

/**
 * Event payload instructing a {@link FogDevice} to change the health state of one or more servers.
 */
public class FogServerStateChange {

    private final String serverId;
    private final FogServerHealthState targetState;
    private final boolean applyToAllServers;

    public FogServerStateChange(String serverId, FogServerHealthState targetState) {
        this(serverId, targetState, false);
    }

    public FogServerStateChange(boolean applyToAllServers, FogServerHealthState targetState) {
        this(null, targetState, applyToAllServers);
    }

    private FogServerStateChange(String serverId, FogServerHealthState targetState, boolean applyToAllServers) {
        this.serverId = serverId;
        this.targetState = targetState;
        this.applyToAllServers = applyToAllServers;
    }

    public String getServerId() {
        return serverId;
    }

    public FogServerHealthState getTargetState() {
        return targetState;
    }

    public boolean isApplyToAllServers() {
        return applyToAllServers;
    }
}
