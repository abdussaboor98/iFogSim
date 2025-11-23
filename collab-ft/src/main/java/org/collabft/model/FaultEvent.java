package org.collabft.model;

/**
 * Represents a scheduled fault and its lifecycle times.
 */
public class FaultEvent {

    public enum Type {
        CPU_DROP,
        SERVER_CRASH,
        BW_DEGRADATION
    }

    private int fogNodeId;
    private int serverId;
    private Type type;
    private double predictedAt;
    private double faultAt;
    private double recoverAt;

    public FaultEvent() {
        // Bean constructor
    }

    public FaultEvent(int fogNodeId, int serverId, Type type, double predictedAt, double faultAt, double recoverAt) {
        this.fogNodeId = fogNodeId;
        this.serverId = serverId;
        this.type = type;
        this.predictedAt = predictedAt;
        this.faultAt = faultAt;
        this.recoverAt = recoverAt;
    }

    public int getFogNodeId() {
        return fogNodeId;
    }

    public void setFogNodeId(int fogNodeId) {
        this.fogNodeId = fogNodeId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public double getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(double predictedAt) {
        this.predictedAt = predictedAt;
    }

    public double getFaultAt() {
        return faultAt;
    }

    public void setFaultAt(double faultAt) {
        this.faultAt = faultAt;
    }

    public double getRecoverAt() {
        return recoverAt;
    }

    public void setRecoverAt(double recoverAt) {
        this.recoverAt = recoverAt;
    }

    public double timeUntilFault(double now) {
        return Math.max(0.0, faultAt - now);
    }

    public double timeUntilRecover(double now) {
        return Math.max(0.0, recoverAt - now);
    }
}
