package org.collabft.model;

/**
 * Signals that a container finished execution on a host.
 */
public class CompletionNotice {
    private final ContainerModule container;
    private final String hostName;
    private final double start;
    private final double finish;

    public CompletionNotice(ContainerModule container, String hostName, double start, double finish) {
        this.container = container;
        this.hostName = hostName;
        this.start = start;
        this.finish = finish;
    }

    public ContainerModule getContainer() {
        return container;
    }

    public String getHostName() {
        return hostName;
    }

    public double getStart() {
        return start;
    }

    public double getFinish() {
        return finish;
    }
}
