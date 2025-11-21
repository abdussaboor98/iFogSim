package org.fog.entities.container;

/**
 * Describes a migration request for a container between two hosts.
 */
public class ContainerMigrationRequest {
    private final ContainerInstance container;
    private final ContainerHost source;
    private final ContainerHost destination;
    private final double networkLatency;
    private final double minimumBandwidth;

    public ContainerMigrationRequest(
            ContainerInstance container,
            ContainerHost source,
            ContainerHost destination,
            double networkLatency,
            double minimumBandwidth) {
        this.container = container;
        this.source = source;
        this.destination = destination;
        this.networkLatency = networkLatency;
        this.minimumBandwidth = minimumBandwidth;
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

    public double getNetworkLatency() {
        return networkLatency;
    }

    public double getMinimumBandwidth() {
        return minimumBandwidth;
    }
}
