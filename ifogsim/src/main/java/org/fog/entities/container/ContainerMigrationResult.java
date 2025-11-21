package org.fog.entities.container;

/**
 * Immutable description of the outcome of a migration attempt.
 */
public class ContainerMigrationResult {
    private final ContainerMigrationTask task;
    private final ContainerInstance container;
    private final ContainerHost source;
    private final ContainerHost destination;
    private final boolean success;
    private final String message;
    private final double eventTime;

    private ContainerMigrationResult(
            ContainerMigrationTask task,
            ContainerInstance container,
            ContainerHost source,
            ContainerHost destination,
            boolean success,
            String message,
            double eventTime) {
        this.task = task;
        this.container = container;
        this.source = source;
        this.destination = destination;
        this.success = success;
        this.message = message;
        this.eventTime = eventTime;
    }

    public static ContainerMigrationResult success(ContainerMigrationTask task, double eventTime) {
        return new ContainerMigrationResult(
                task,
                task.getContainer(),
                task.getSource(),
                task.getDestination(),
                true,
                "Migration completed successfully",
                eventTime);
    }

    public static ContainerMigrationResult failure(ContainerMigrationTask task, String message, double eventTime) {
        return new ContainerMigrationResult(
                task,
                task.getContainer(),
                task.getSource(),
                task.getDestination(),
                false,
                message,
                eventTime);
    }

    public static ContainerMigrationResult failure(ContainerMigrationRequest request, String message, double eventTime) {
        return new ContainerMigrationResult(
                null,
                request.getContainer(),
                request.getSource(),
                request.getDestination(),
                false,
                message,
                eventTime);
    }

    public ContainerMigrationTask getTask() {
        return task;
    }

    public ContainerInstance getContainer() {
        return container;
    }

    public ContainerHost getSource() {
        return source;
    }

    public ContainerHost getDestination() {
        return destination;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public double getEventTime() {
        return eventTime;
    }
}
