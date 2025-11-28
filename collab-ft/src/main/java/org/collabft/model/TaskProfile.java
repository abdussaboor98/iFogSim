package org.collabft.model;

import java.util.UUID;

/**
 * Describes a task arriving from an edge device.
 */
public class TaskProfile {
    private final String taskId = UUID.randomUUID().toString();
    private final ContainerProfile containerProfile;
    private final double arrivalTime;
    private final double deadlineTime;
    private final double tExecSeconds;
    private final double tNetSeconds;
    private final double tSlackSeconds;
    private final double tMigSeconds;
    private final String originatingEdge;

    public TaskProfile(ContainerProfile containerProfile, double arrivalTime, double deadlineTime,
                       double tExecSeconds, double tNetSeconds, double tSlackSeconds, double tMigSeconds,
                       String originatingEdge) {
        this.containerProfile = containerProfile;
        this.arrivalTime = arrivalTime;
        this.deadlineTime = deadlineTime;
        this.tExecSeconds = tExecSeconds;
        this.tNetSeconds = tNetSeconds;
        this.tSlackSeconds = tSlackSeconds;
        this.tMigSeconds = tMigSeconds;
        this.originatingEdge = originatingEdge;
    }

    public String getTaskId() {
        return taskId;
    }

    public ContainerProfile getContainerProfile() {
        return containerProfile;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public double getDeadlineTime() {
        return deadlineTime;
    }

    public double getTExecSeconds() {
        return tExecSeconds;
    }

    public double getTNetSeconds() {
        return tNetSeconds;
    }

    public double getTSlackSeconds() {
        return tSlackSeconds;
    }

    public double getTMigSeconds() {
        return tMigSeconds;
    }

    public String getOriginatingEdge() {
        return originatingEdge;
    }
}
