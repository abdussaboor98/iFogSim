package org.fog.entities.container;

/**
 * Represents an in-flight migration managed by the {@link ContainerMigrationManager}.
 */
public class ContainerMigrationTask {

    private final int id;
    private final ContainerInstance container;
    private final ContainerHost source;
    private final ContainerHost destination;
    private final double duration;
    private final double bandwidth;
    private final double networkLatency;
    private final double requestTime;

    private double startTime;
    private double endTime;

    public ContainerMigrationTask(
            int id,
            ContainerInstance container,
            ContainerHost source,
            ContainerHost destination,
            double duration,
            double bandwidth,
            double networkLatency,
            double requestTime) {
        this.id = id;
        this.container = container;
        this.source = source;
        this.destination = destination;
        this.duration = duration;
        this.bandwidth = bandwidth;
        this.networkLatency = networkLatency;
        this.requestTime = requestTime;
    }

    public int getId() {
        return id;
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

    public double getDuration() {
        return duration;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public double getNetworkLatency() {
        return networkLatency;
    }

    public double getRequestTime() {
        return requestTime;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }
}
