package org.fog.entities.container;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.utils.FogEvents;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles container migrations between any {@link ContainerHost}s.
 */
public class ContainerMigrationManager extends SimEntity {

    private final Map<Integer, ContainerMigrationTask> activeMigrations = new HashMap<>();
    private int nextMigrationId = 1;

    public ContainerMigrationManager(String name) {
        super(name);
    }

    public void requestMigration(ContainerMigrationRequest request) {
        sendNow(getId(), FogEvents.CONTAINER_MIGRATION_REQUEST, request);
    }

    public void scheduleMigration(ContainerMigrationRequest request, double delay) {
        send(getId(), delay, FogEvents.CONTAINER_MIGRATION_REQUEST, request);
    }

    @Override
    public void startEntity() {
        // Nothing to schedule proactively, the manager reacts to requests.
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.CONTAINER_MIGRATION_REQUEST:
                handleMigrationRequest((ContainerMigrationRequest) ev.getData());
                break;
            case FogEvents.CONTAINER_MIGRATION_START:
                handleMigrationStart((ContainerMigrationTask) ev.getData());
                break;
            case FogEvents.CONTAINER_MIGRATION_COMPLETE:
                if (ev.getData() instanceof ContainerMigrationTask) {
                    finalizeMigration((ContainerMigrationTask) ev.getData());
                } else if (ev.getData() instanceof ContainerMigrationResult) {
                    logMigrationResult((ContainerMigrationResult) ev.getData());
                }
                break;
            case FogEvents.CONTAINER_MIGRATION_FAILED:
                logMigrationResult((ContainerMigrationResult) ev.getData());
                break;
            default:
                break;
        }
    }

    @Override
    public void shutdownEntity() {
        activeMigrations.clear();
    }

    private void handleMigrationRequest(ContainerMigrationRequest request) {
        ContainerInstance container = request.getContainer();
        ContainerHost source = request.getSource();
        ContainerHost destination = request.getDestination();
        if (container.getCurrentHost() != source) {
            publishImmediateFailure(request, "Container is not running on the declared source host");
            return;
        }
        if (container.getContainerState() == ContainerState.MIGRATING) {
            publishImmediateFailure(request, "Container is already migrating");
            return;
        }
        if (!destination.canHost(container)) {
            publishImmediateFailure(request, "Destination host does not have enough free resources");
            return;
        }
        double sourceBw = Math.max(1.0, source.getAvailableBw());
        double destBw = Math.max(1.0, destination.getAvailableBw());
        double availableBandwidth = Math.max(Math.min(sourceBw, destBw), request.getMinimumBandwidth());
        double checkpointSize = Math.max(1.0, container.getCheckpointSize());
        double duration = checkpointSize / availableBandwidth + request.getNetworkLatency();
        ContainerMigrationTask task = new ContainerMigrationTask(
                nextMigrationId++,
                container,
                source,
                destination,
                duration,
                availableBandwidth,
                request.getNetworkLatency(),
                CloudSim.clock());
        activeMigrations.put(task.getId(), task);
        sendNow(getId(), FogEvents.CONTAINER_MIGRATION_START, task);
    }

    private void handleMigrationStart(ContainerMigrationTask task) {
        task.setStartTime(CloudSim.clock());
        ContainerInstance container = task.getContainer();
        task.getSource().pauseContainer(container);
        container.markMigrating();
        container.createCheckpoint(task.getStartTime());
        send(getId(), task.getDuration(), FogEvents.CONTAINER_MIGRATION_COMPLETE, task);
    }

    private void finalizeMigration(ContainerMigrationTask task) {
        task.setEndTime(CloudSim.clock());
        activeMigrations.remove(task.getId());

        ContainerInstance container = task.getContainer();
        ContainerHost source = task.getSource();
        ContainerHost destination = task.getDestination();

        source.deallocateContainer(container);
        if (destination.allocateContainer(container)) {
            destination.resumeContainer(container);
            container.createCheckpoint(task.getEndTime());
            ContainerMigrationResult result = ContainerMigrationResult.success(task, CloudSim.clock());
            sendNow(getId(), FogEvents.CONTAINER_MIGRATION_COMPLETE, result);
        } else {
            boolean rolledBack = source.allocateContainer(container);
            if (rolledBack) {
                source.resumeContainer(container);
            } else {
                container.markFailed();
            }
            publishFailure(task, "Destination host ran out of resources during migration");
        }
    }

    private void publishFailure(ContainerMigrationTask task, String reason) {
        ContainerMigrationResult result = ContainerMigrationResult.failure(task, reason, CloudSim.clock());
        sendNow(getId(), FogEvents.CONTAINER_MIGRATION_FAILED, result);
    }

    private void publishImmediateFailure(ContainerMigrationRequest request, String reason) {
        ContainerMigrationResult result = ContainerMigrationResult.failure(request, reason, CloudSim.clock());
        sendNow(getId(), FogEvents.CONTAINER_MIGRATION_FAILED, result);
    }

    private void logMigrationResult(ContainerMigrationResult result) {
        String status = result.isSuccess() ? "SUCCESS" : "FAILURE";
        String containerName = result.getContainer() != null ? result.getContainer().getName() : "unknown";
        String sourceName = result.getSource() != null ? result.getSource().getHostName() : "unknown";
        String destName = result.getDestination() != null ? result.getDestination().getHostName() : "unknown";
        Log.printLine(String.format(
                "[%s] Container migration %s -> %s for %s at t=%.2f : %s",
                status,
                sourceName,
                destName,
                containerName,
                result.getEventTime(),
                result.getMessage()));
    }
}
