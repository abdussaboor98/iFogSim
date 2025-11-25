package org.collabft.model;

/**
 * Payload used to request migration of a container (bid solicitation).
 */
public class MigrationRequest {
    private final ContainerModule container;
    private final int originId;

    public MigrationRequest(ContainerModule container, int originId) {
        this.container = container;
        this.originId = originId;
    }

    public ContainerModule getContainer() {
        return container;
    }

    public int getOriginId() {
        return originId;
    }
}
