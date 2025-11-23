package org.collabft.model;

/**
 * Sent from an origin fog node to potential bidders with the container profile.
 */
public class BidRequest {

    private long requestId;
    private int originFogId;
    private int targetFogId;
    private ContainerProfile container;
    private double createdAt;
    private double deadlineSec;
    private double timeoutSec;

    public BidRequest() {
        // Bean constructor
    }

    public BidRequest(long requestId, int originFogId, int targetFogId, ContainerProfile container,
                      double createdAt, double deadlineSec, double timeoutSec) {
        this.requestId = requestId;
        this.originFogId = originFogId;
        this.targetFogId = targetFogId;
        this.container = container;
        this.createdAt = createdAt;
        this.deadlineSec = deadlineSec;
        this.timeoutSec = timeoutSec;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getOriginFogId() {
        return originFogId;
    }

    public void setOriginFogId(int originFogId) {
        this.originFogId = originFogId;
    }

    public int getTargetFogId() {
        return targetFogId;
    }

    public void setTargetFogId(int targetFogId) {
        this.targetFogId = targetFogId;
    }

    public ContainerProfile getContainer() {
        return container;
    }

    public void setContainer(ContainerProfile container) {
        this.container = container;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getDeadlineSec() {
        return deadlineSec;
    }

    public void setDeadlineSec(double deadlineSec) {
        this.deadlineSec = deadlineSec;
    }

    public double getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(double timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public boolean isExpired(double now) {
        return now - createdAt > timeoutSec;
    }

    public double age(double now) {
        return Math.max(0.0, now - createdAt);
    }
}
