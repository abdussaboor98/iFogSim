package org.collabft.model;

import org.collabft.agents.FogServer;

/**
 * Carries fault prediction/occurrence details.
 */
public class FaultNotice {
    public enum FaultType { CPU_FAILURE, BANDWIDTH_DEGRADATION, SERVER_CRASH }

    private final FogServer server;
    private final FaultType type;
    private final boolean prediction;
    private final double failureTime;
    private final int controllerId;

    public FaultNotice(FogServer server, FaultType type, boolean prediction, double failureTime, int controllerId) {
        this.server = server;
        this.type = type;
        this.prediction = prediction;
        this.failureTime = failureTime;
        this.controllerId = controllerId;
    }

    public FogServer getServer() {
        return server;
    }

    public FaultType getType() {
        return type;
    }

    public boolean isPrediction() {
        return prediction;
    }

    public double getFailureTime() {
        return failureTime;
    }

    public int getControllerId() {
        return controllerId;
    }
}
