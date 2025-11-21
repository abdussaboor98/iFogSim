package org.fog.entities.container;

/**
 * Represents the high level execution state of a containerized task.
 */
public enum ContainerState {
    READY,
    RUNNING,
    PAUSED,
    MIGRATING,
    COMPLETED,
    FAILED
}
