package org.collabft.model;

import java.util.UUID;

/**
 * Describes a task arriving from an edge device.
 */
public class TaskProfile {
    private final String taskId = UUID.randomUUID().toString();
    private final ContainerProfile containerProfile;
    private final double arrivalTime;

    public TaskProfile(ContainerProfile containerProfile, double arrivalTime) {
        this.containerProfile = containerProfile;
        this.arrivalTime = arrivalTime;
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
}
