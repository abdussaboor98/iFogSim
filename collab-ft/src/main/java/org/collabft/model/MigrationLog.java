package org.collabft.model;

/**
 * Lightweight record of a migration attempt for metrics aggregation.
 */
public class MigrationLog {

    public enum Phase {
        LOCAL,
        INTER_FOG,
        CLOUD
    }

    private String containerId;
    private int sourceFogId;
    private int targetFogId;
    private int sourceServerId;
    private int targetServerId;
    private Phase phase;
    private double startedAt;
    private double completedAt;
    private boolean success;
    private boolean deadlineMet;
    private String failureReason;

    public MigrationLog() {
        // Bean constructor
    }

    public MigrationLog(String containerId, int sourceFogId, int targetFogId, int sourceServerId, int targetServerId,
                        Phase phase, double startedAt) {
        this.containerId = containerId;
        this.sourceFogId = sourceFogId;
        this.targetFogId = targetFogId;
        this.sourceServerId = sourceServerId;
        this.targetServerId = targetServerId;
        this.phase = phase;
        this.startedAt = startedAt;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public int getSourceFogId() {
        return sourceFogId;
    }

    public void setSourceFogId(int sourceFogId) {
        this.sourceFogId = sourceFogId;
    }

    public int getTargetFogId() {
        return targetFogId;
    }

    public void setTargetFogId(int targetFogId) {
        this.targetFogId = targetFogId;
    }

    public int getSourceServerId() {
        return sourceServerId;
    }

    public void setSourceServerId(int sourceServerId) {
        this.sourceServerId = sourceServerId;
    }

    public int getTargetServerId() {
        return targetServerId;
    }

    public void setTargetServerId(int targetServerId) {
        this.targetServerId = targetServerId;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public double getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(double startedAt) {
        this.startedAt = startedAt;
    }

    public double getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(double completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isDeadlineMet() {
        return deadlineMet;
    }

    public void setDeadlineMet(boolean deadlineMet) {
        this.deadlineMet = deadlineMet;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public double duration() {
        if (completedAt <= 0 || startedAt <= 0) {
            return 0.0;
        }
        return Math.max(0.0, completedAt - startedAt);
    }
}
